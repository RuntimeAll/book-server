package org.dromara.book.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.entity.BizQuestion;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 试卷 PDF 拼装器（OpenPDF，纯图模式，PRD-A-014）。
 *
 * <p>按预览版式拼 PDF：A4 纵向，每题 = 题号 + 题干图；variant=teacher 时题后跟答案图 +
 * 解析图；variant=student_answers_appended 时答案图集中附在卷末。图片按宽等比缩放进内容区
 * （上下左右各留 ~10mm）。水印开启时每页平铺半透明斜排 "老师名 手机号脱敏"。
 *
 * <p>题图字节由 {@link OssImageFetcher} 拉（共用 SSRF 白名单 + Redis 缓存）。中文走 OpenPDF 内置
 * {@code STSong-Light + UniGB-UCS2-H}（CJK，OpenPDF 主 jar 自带亚洲字体，水印/题号中文不乱码）。
 *
 * <p>纯无状态组件，无 web/DB 依赖，由 {@code ExportPdfWorker} 在异步线程池中调用。
 *
 * @author backend-dev
 */
@Slf4j
@Component
public class PdfComposer {

    /** 页边距（pt，约 28pt ≈ 10mm） */
    private static final float MARGIN = 28f;

    /** 内容区可用宽度（A4 宽 595pt - 左右边距） */
    private static final float CONTENT_WIDTH = PageSize.A4.getWidth() - 2 * MARGIN;

    private final OssImageFetcher ossImageFetcher;

    public PdfComposer(OssImageFetcher ossImageFetcher) {
        this.ossImageFetcher = ossImageFetcher;
    }

    /**
     * 卷型枚举（与契约 variant 串对齐）。
     */
    public enum Variant {
        /** 学生卷：仅题干 */
        STUDENT,
        /** 教师卷：每题后跟答案图 + 解析图 */
        TEACHER,
        /** 学生卷 + 答案集中附后 */
        STUDENT_ANSWERS_APPENDED;

        public static Variant of(String s) {
            if (s == null) {
                return STUDENT;
            }
            return switch (s) {
                case "teacher" -> TEACHER;
                case "student_answers_appended" -> STUDENT_ANSWERS_APPENDED;
                default -> STUDENT;
            };
        }
    }

    /**
     * 拼 PDF 并返回字节。
     *
     * @param title         卷名（首行标题）
     * @param questions     题目（已按导出题序排好）
     * @param variant       卷型
     * @param watermark     是否加水印
     * @param watermarkText 水印文字（"老师名 138****1234"），watermark=true 时用
     * @return PDF 字节
     */
    public byte[] compose(String title, List<BizQuestion> questions, Variant variant,
                          boolean watermark, String watermarkText) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            if (watermark) {
                writer.setPageEvent(new WatermarkEvent(watermarkText, cjkFont(20f)));
            }
            document.open();

            Font titleFont = cjkFont(18f);
            Font qFont = cjkFont(12f);

            // 卷名标题
            Paragraph titlePara = new Paragraph(title == null ? "试卷" : title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(16f);
            document.add(titlePara);

            int qNo = 1;
            for (BizQuestion q : questions) {
                // 题号
                document.add(new Paragraph(qNo + ".", qFont));
                addImageIfPresent(document, q.getStemImgUrl());
                if (variant == Variant.TEACHER) {
                    // 教师卷：答案 + 解析跟在题后
                    addLabeledImage(document, qFont, "【答案】", q.getAnswerImgUrl());
                    addLabeledImage(document, qFont, "【解析】", q.getExplainImgUrl());
                }
                document.add(spacer());
                qNo++;
            }

            // 学生卷 + 答案附后：卷末集中放答案图
            if (variant == Variant.STUDENT_ANSWERS_APPENDED) {
                document.newPage();
                Paragraph ansTitle = new Paragraph("参考答案", titleFont);
                ansTitle.setAlignment(Element.ALIGN_CENTER);
                ansTitle.setSpacingAfter(16f);
                document.add(ansTitle);
                int aNo = 1;
                for (BizQuestion q : questions) {
                    document.add(new Paragraph(aNo + ".", qFont));
                    addImageIfPresent(document, q.getAnswerImgUrl());
                    document.add(spacer());
                    aNo++;
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            if (document.isOpen()) {
                document.close();
            }
            throw new RuntimeException("PDF 生成失败: " + e.getMessage(), e);
        }
    }

    /** 题间距 */
    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(10f);
        return p;
    }

    private void addLabeledImage(Document document, Font font, String label, String url) throws DocumentException {
        if (url == null || url.isBlank() || !ossImageFetcher.isWhitelisted(url)) {
            return;
        }
        document.add(new Paragraph(label, font));
        addImageIfPresent(document, url);
    }

    /**
     * 拉图、等比缩放进内容区宽度、居左放入。缺图 / 非白名单 / 拉取失败 → 跳过（不让单题坏掉整卷）。
     */
    private void addImageIfPresent(Document document, String url) throws DocumentException {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!ossImageFetcher.isWhitelisted(url)) {
            // 🔴 别静默吞：2026-06-11 自验踩出 misikt COS host 没进白名单时整卷题图被无声跳过、PDF 只剩水印
            log.warn("[pdf-composer] 题图非白名单 host 跳过 url={}", url);
            return;
        }
        try {
            OssImageFetcher.Fetched fetched = ossImageFetcher.fetch(url);
            com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(fetched.bytes());
            if (img.getWidth() > CONTENT_WIDTH) {
                img.scaleToFit(CONTENT_WIDTH, img.getHeight());
            }
            img.setSpacingBefore(2f);
            img.setSpacingAfter(4f);
            document.add(img);
        } catch (Exception e) {
            log.warn("[pdf-composer] 题图跳过(拉取/解码失败) url={} err={}", url, e.getMessage());
        }
    }

    /**
     * CJK 字体（STSong-Light + UniGB-UCS2-H，OpenPDF 内置亚洲字体，中文不乱码）。
     */
    private Font cjkFont(float size) {
        try {
            BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(bf, size);
        } catch (Exception e) {
            // 兜底（理论不会到这）：默认字体，中文会乱码但不崩
            log.warn("[pdf-composer] STSong-Light 加载失败，降级默认字体: {}", e.getMessage());
            return FontFactory.getFont(FontFactory.HELVETICA, size);
        }
    }

    /**
     * 水印页事件：每页平铺半透明斜排文字。
     */
    private static class WatermarkEvent extends PdfPageEventHelper {
        private final String text;
        private final Font font;

        WatermarkEvent(String text, Font font) {
            this.text = text == null ? "" : text;
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (text.isBlank()) {
                return;
            }
            PdfContentByte canvas = writer.getDirectContentUnder();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.12f);
            canvas.saveState();
            canvas.setGState(gs);
            Rectangle pageSize = document.getPageSize();
            Phrase phrase = new Phrase(text, font);
            // 平铺：每隔 ~180pt 一个，斜排 45 度
            for (float y = 0; y < pageSize.getHeight() + 180; y += 180) {
                for (float x = 0; x < pageSize.getWidth() + 180; x += 220) {
                    ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, phrase, x, y, 45f);
                }
            }
            canvas.restoreState();
        }
    }
}
