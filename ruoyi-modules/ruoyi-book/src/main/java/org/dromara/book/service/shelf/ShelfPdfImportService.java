package org.dromara.book.service.shelf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.dromara.book.domain.bo.ShelfBookBo;
import org.dromara.book.domain.entity.BizShelfBook;
import org.dromara.book.mapper.BizShelfBookMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF 直录待解析书（轻挂载）。
 *
 * <p>用途：手上一份还没拆题/还没建结构的 PDF，先「挂」进书架占位——原件进 OSS、第 1 页渲成封面、
 * 建一本 {@code book_type=pdf_pending} 的空壳书，后续再由人工或录题管线把它解析成真书。
 * 与电子课本 {@code textbook} 严格区分：pdf_pending 没有节点树/内容项，只有 style_meta 里的三件套。
 *
 * <p>落位（零 DDL）：{@code biz_shelf_book.style_meta_json} 合并写
 * {@code {pdfUrl, pdfPages, coverUrl}}；title/grade/subject_id 落常规列；
 * 建书复用 {@link ShelfService#createBook}（owner=LoginHelper 服务端强制、租户/审计列都在那条正路上）。
 *
 * <p>封面渲染沿用库内既有范式（{@code ReviewService.sourcePage} / {@code ScheduleRenderUtil}）：
 * PDFBox {@link PDFRenderer#renderImageWithDPI} → {@link ImageIO} PNG → OSS。
 * 渲染失败<b>不阻断</b>建书（coverUrl=null，书照样挂上，FE 显示占位图即可）。
 *
 * @author backend-dev
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShelfPdfImportService {

    private final ShelfService shelfService;
    private final BizShelfBookMapper bookMapper;

    /** 单个 PDF 上限 100MB（与 spring.servlet.multipart.max-file-size 对齐；超限 Spring 先拒，这里双保险出人话）。 */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 封面渲染 DPI（96 = 屏幕基准，A4 约 794×1123px，够卡片缩略图用且体积可控）。 */
    private static final float COVER_DPI = 96f;

    /** PDF 魔数：文件头必须是 %PDF（不信扩展名/Content-Type）。 */
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};

    /**
     * PDF 直录：原件→OSS、第 1 页→封面 PNG→OSS、建 {@code pdf_pending} 空壳书。
     *
     * @return {id, title, bookType, pdfUrl, pdfPages, coverUrl}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importPdf(MultipartFile file, String title, String grade, String subjectId,
                                         String edition, String unit) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空", 400);
        }
        if (StringUtils.isBlank(title)) {
            throw new ServiceException("title 不能为空", 400);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("PDF 不能超过 100MB（收到 " + file.getSize() / 1024 / 1024 + "MB）", 400);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败：" + e.getMessage(), 500);
        }
        if (!isPdf(bytes)) {
            throw new ServiceException("不是 PDF 文件（文件头缺 %PDF 魔数）", 400);
        }

        // ① 原件进 OSS
        OssClient oss = OssFactory.instance();
        UploadResult pdfUp = oss.uploadSuffix(bytes, ".pdf", "application/pdf");
        String pdfUrl = pdfUp.getUrl();

        // ② 页数 + 第 1 页封面（渲染失败不阻断建书）
        int pdfPages = 0;
        String coverUrl = null;
        try (PDDocument doc = PDDocument.load(bytes)) {
            pdfPages = doc.getNumberOfPages();
            if (pdfPages > 0) {
                BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, COVER_DPI);
                ByteArrayOutputStream os = new ByteArrayOutputStream(1 << 19);
                if (ImageIO.write(img, "png", os)) {
                    coverUrl = oss.uploadSuffix(os.toByteArray(), ".png", "image/png").getUrl();
                } else {
                    log.warn("[shelf-pdf] 封面 PNG 编码失败（无 writer），跳过封面 title={}", title);
                }
            }
        } catch (Exception e) {
            log.warn("[shelf-pdf] 封面渲染失败，按无封面继续建书 title={}", title, e);
        }
        if (pdfPages <= 0) {
            throw new ServiceException("PDF 解析不出页数（文件可能已损坏）", 400);
        }

        // ③ 建书走 ShelfService 正路（owner/审计列不绕过）
        ShelfBookBo bo = new ShelfBookBo();
        bo.setTitle(title.trim());
        bo.setBookType(ShelfService.BOOK_TYPE_PDF_PENDING);
        // 🔴 grade 口径=年级+册合一（"七年级上"），与书架 年级/册 筛选同源——调用方按此传
        bo.setGrade(StringUtils.trimToNull(grade));
        bo.setSubjectId(StringUtils.trimToNull(subjectId));
        bo.setEdition(StringUtils.trimToNull(edition));
        Long bookId = shelfService.createBook(bo);

        // ④ style_meta 合并写三件套 + 章节（unit：表无列，挂 style_meta，FE 卡片展示/检索用）
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("pdfUrl", pdfUrl);
        patch.put("pdfPages", pdfPages);
        patch.put("coverUrl", coverUrl);
        if (StringUtils.isNotBlank(unit)) {
            patch.put("unit", unit.trim());
        }
        BizShelfBook fresh = bookMapper.selectById(bookId);
        shelfService.mergeStyleMeta(fresh, patch);

        log.info("[shelf-pdf] 直录 bookId={} title={} pages={} size={}B pdf={} cover={}",
            bookId, title, pdfPages, bytes.length, pdfUrl, coverUrl);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(bookId));
        r.put("title", bo.getTitle());
        r.put("bookType", ShelfService.BOOK_TYPE_PDF_PENDING);
        r.put("pdfUrl", pdfUrl);
        r.put("pdfPages", pdfPages);
        r.put("coverUrl", coverUrl);
        return r;
    }

    /** 魔数校验：文件头 4 字节 = {@code %PDF}。 */
    private boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < PDF_MAGIC.length) return false;
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) return false;
        }
        return true;
    }
}
