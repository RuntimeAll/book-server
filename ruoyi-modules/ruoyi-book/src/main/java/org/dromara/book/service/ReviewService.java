package org.dromara.book.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizQuestionBlock;
import org.dromara.book.domain.entity.BizReviewIssue;
import org.dromara.book.domain.entity.BizReviewPage;
import org.dromara.book.domain.entity.BizShelfItem;
import org.dromara.book.domain.entity.BizShelfNode;
import org.dromara.book.mapper.BizQuestionBlockMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizReviewIssueMapper;
import org.dromara.book.mapper.BizReviewPageMapper;
import org.dromara.book.mapper.BizShelfItemMapper;
import org.dromara.book.mapper.BizShelfNodeMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 录题比对审核 Service（PRD-006 批1，/teacher/review）。
 *
 * <p>四本人教版空白讲义的「源 PDF ↔ 已录题」页级人工比对：进度统计、源页渲染成 PNG、
 * 页内题项拉取、页级确认、问题登记（跨书沉淀转录改进原料）。
 *
 * <p>🔴 只读题库（biz_shelf_item / biz_question / biz_question_block），只写审核两表
 * （biz_review_page / biz_review_issue）——绝不触碰答案 biz_text_content / 血缘 / is_public / 题数。
 * 源 PDF 路径为本机常量映射（批1 硬配置，四本空白卷版）。
 *
 * @author backend-dev (PRD-006 批1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final BizReviewPageMapper reviewPageMapper;
    private final BizReviewIssueMapper reviewIssueMapper;
    private final BizShelfItemMapper shelfItemMapper;
    private final BizQuestionMapper questionMapper;
    private final BizQuestionBlockMapper questionBlockMapper;
    private final BizShelfNodeMapper shelfNodeMapper;

    /** 问题登记默认状态。 */
    private static final String ISSUE_STATUS_DEFAULT = "待处理";

    /** 源页渲染 DPI（144 = 屏幕比对清晰且体积可控）。 */
    private static final float RENDER_DPI = 144f;

    /**
     * 🔴 四本源 PDF 硬配置（bookId → 本机绝对路径，批1 空白卷版）。
     * 四上文件名 {@code 162页 人教版 .pdf} 结尾含空格，保留原样。
     */
    private static final Map<Long, String> BOOK_PDF = Map.ofEntries(
        // 首批 4 本（原空白卷版）
        Map.entry(2077068328978886658L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新一上人教版数学同步典例考点讲义\\人教版一年级上册数学单元同步讲义—空白题目.pdf"),
        Map.entry(2077059473926533122L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新一下人教版数学同步典例考点讲义\\空白\\26新版一下同步讲义1-6单元＋期末复习汇总（原卷版）.pdf"),
        Map.entry(2077059011563237377L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新四上人教版数学同步典例考点讲义\\空白题目\\26新版四上同步讲义汇总（原卷版）162页 人教版 .pdf"),
        Map.entry(2077045318033092610L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新四下人教版数学同步典例考点讲义\\空白\\四下数学同步讲义1-9单元（原卷版）162页 人教版.pdf"),
        // 2026-07-21 全量加入审核（8 本，原卷/空白版；二上无空白版用同步讲义 PDF）
        Map.entry(2077094124430831617L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新二上人教版数学同步典例考点讲义\\人教版二年级上册数学单元同步讲义.pdf"),
        Map.entry(2077060532367536130L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新二下人教版数学同步典例考点讲义\\空白\\26新版二下同步讲义1-4单元汇总（原卷版）.pdf"),
        Map.entry(2079094415388921858L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新三上人教版数学同步典例考点讲义\\空白题目\\26新版三上同步讲义汇总（原卷版）140页 人教版.pdf"),
        Map.entry(2077057421011857409L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新三下人教版数学同步典例考点讲义\\空白（word格式＋PDF）\\26新版三下同步讲义1-6单元汇总（原卷版）.pdf"),
        Map.entry(2077037667660345346L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新五上人教版数学同步典例考点讲义\\26新版五上同步讲义汇总（原卷版）236页 人教版 .pdf"),
        Map.entry(2077067475643543553L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新五下人教版数学同步典例考点讲义\\五下1-8单元汇总（同步讲义）\\空白\\26新版五下同步讲义1-8单元汇总（原卷版）.pdf"),
        Map.entry(2077068347467378689L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新六上人教版数学同步典例考点讲义\\26新版六上同步讲义汇总（原卷版）294页 人教版 .pdf"),
        Map.entry(2077065957511016449L, "D:\\workplace\\ai-bkb\\测试数据\\小学数学所有内容\\新六下人教版数学同步典例考点讲义\\空白\\26新版六下同步讲义1-5单元汇总（原卷版）187页 人教版.pdf")
    );

    // ───────────────── 进度 ─────────────────

    /** 审核进度：{totalPages, reviewedPages}。totalPages 优先源 PDF 页数，缺则回退 source_page 最大值。 */
    public Map<String, Object> progress(Long bookId) {
        requireBookId(bookId);
        Integer totalPages = pdfPageCount(bookId);
        if (totalPages == null) {
            totalPages = maxSourcePage(bookId);
        }
        Long reviewed = reviewPageMapper.selectCount(new LambdaQueryWrapper<BizReviewPage>()
            .eq(BizReviewPage::getBookId, bookId)
            .eq(BizReviewPage::getReviewed, 1));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("totalPages", totalPages == null ? 0 : totalPages);
        r.put("reviewedPages", reviewed == null ? 0 : reviewed.intValue());
        return r;
    }

    private Integer maxSourcePage(Long bookId) {
        List<BizShelfItem> items = shelfItemMapper.selectList(new LambdaQueryWrapper<BizShelfItem>()
            .select(BizShelfItem::getSourcePage)
            .eq(BizShelfItem::getBookId, bookId)
            .isNotNull(BizShelfItem::getSourcePage));
        int max = 0;
        for (BizShelfItem it : items) {
            if (it.getSourcePage() != null && it.getSourcePage() > max) {
                max = it.getSourcePage();
            }
        }
        return max == 0 ? null : max;
    }

    /** 源 PDF 总页数（PDFBox），无配置/文件缺失/读取失败返 null（由调用方回退）。 */
    private Integer pdfPageCount(Long bookId) {
        String path = BOOK_PDF.get(bookId);
        if (path == null) {
            return null;
        }
        File f = new File(path);
        if (!f.isFile()) {
            log.warn("[review] 源 PDF 不存在 bookId={} path={}", bookId, path);
            return null;
        }
        try (PDDocument doc = PDDocument.load(f)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            log.warn("[review] 源 PDF 读取失败 bookId={} : {}", bookId, e.getMessage());
            return null;
        }
    }

    // ───────────────── 源页渲染 PNG ─────────────────

    /**
     * 渲染源 PDF 第 page 页为 PNG（base64 data URL）。
     * 返回 {bookId, page, totalPages, mime, image(data URL), base64}。
     */
    public Map<String, Object> sourcePage(Long bookId, Integer page) {
        requireBookId(bookId);
        if (page == null || page < 1) {
            throw new ServiceException("page 非法（需 >=1）", 400);
        }
        String path = BOOK_PDF.get(bookId);
        if (path == null) {
            throw new ServiceException("该书未配置源 PDF（批1 仅四本空白卷）", 404);
        }
        File f = new File(path);
        if (!f.isFile()) {
            throw new ServiceException("源 PDF 文件不存在：" + path, 404);
        }
        try (PDDocument doc = PDDocument.load(f)) {
            int total = doc.getNumberOfPages();
            if (page > total) {
                throw new ServiceException("page 越界（该书共 " + total + " 页）", 400);
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(page - 1, RENDER_DPI);
            ByteArrayOutputStream os = new ByteArrayOutputStream(1 << 19);
            if (!ImageIO.write(img, "png", os)) {
                throw new ServiceException("PNG 编码失败", 500);
            }
            String b64 = Base64.getEncoder().encodeToString(os.toByteArray());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("bookId", String.valueOf(bookId));
            r.put("page", page);
            r.put("totalPages", total);
            r.put("mime", "image/png");
            r.put("base64", b64);
            r.put("image", "data:image/png;base64," + b64);
            return r;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[review] 源页渲染失败 bookId={} page={}", bookId, page, e);
            throw new ServiceException("源页渲染异常：" + e.getMessage(), 500);
        }
    }

    // ───────────────── 页内题项 ─────────────────

    /**
     * 拉取某源页的已录题项（联 biz_shelf_item + biz_question + biz_question_block）。
     * question → {questionId, kind, stem, blockJson, seq}；explain → {kind:'explain', explainJson, seq}。按 seq 排序。
     */
    public Map<String, Object> pageItems(Long bookId, Integer page) {
        requireBookId(bookId);
        if (page == null || page < 1) {
            throw new ServiceException("page 非法（需 >=1）", 400);
        }
        List<BizShelfItem> items = shelfItemMapper.selectList(new LambdaQueryWrapper<BizShelfItem>()
            .eq(BizShelfItem::getBookId, bookId)
            .eq(BizShelfItem::getSourcePage, page)
            .orderByAsc(BizShelfItem::getSeq)
            .orderByAsc(BizShelfItem::getId));

        // 🔴 两级排序：先节点(考点) seq、再节内 item seq——不同考点的 item seq 各自从 1 起，
        // 全局按 item.seq 排会把多个考点的题交叉穿插（自查批量暴露的整簇错序根因）。
        List<Long> nodeIds = items.stream()
            .map(BizShelfItem::getNodeId)
            .filter(n -> n != null)
            .distinct()
            .collect(Collectors.toList());
        if (nodeIds.size() > 1) {
            Map<Long, Integer> nodeSeq = new LinkedHashMap<>();
            for (BizShelfNode n : shelfNodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
                .select(BizShelfNode::getId, BizShelfNode::getSeq)
                .in(BizShelfNode::getId, nodeIds))) {
                nodeSeq.put(n.getId(), n.getSeq() == null ? 0 : n.getSeq());
            }
            items.sort(java.util.Comparator
                .comparingInt((BizShelfItem it) -> nodeSeq.getOrDefault(it.getNodeId(), 0))
                .thenComparingInt(it -> it.getSeq() == null ? 0 : it.getSeq())
                .thenComparingLong(BizShelfItem::getId));
        }

        // 批量取题干 + blockJson
        List<Long> qids = items.stream()
            .map(BizShelfItem::getQuestionId)
            .filter(q -> q != null)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> stemById = new LinkedHashMap<>();
        Map<Long, String> blockById = new LinkedHashMap<>();
        if (!qids.isEmpty()) {
            for (BizQuestion q : questionMapper.selectList(new LambdaQueryWrapper<BizQuestion>()
                .select(BizQuestion::getId, BizQuestion::getStemText)
                .in(BizQuestion::getId, qids))) {
                stemById.put(q.getId(), q.getStemText());
            }
            for (BizQuestionBlock b : questionBlockMapper.selectList(new LambdaQueryWrapper<BizQuestionBlock>()
                .select(BizQuestionBlock::getQuestionId, BizQuestionBlock::getBlockJson)
                .in(BizQuestionBlock::getQuestionId, qids))) {
                blockById.put(b.getQuestionId(), b.getBlockJson());
            }
        }

        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (BizShelfItem it : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", String.valueOf(it.getId()));
            m.put("seq", it.getSeq());
            if ("explain".equalsIgnoreCase(it.getKind())) {
                m.put("kind", "explain");
                m.put("explainJson", parseJson(it.getExplainJson()));
            } else {
                Long qid = it.getQuestionId();
                m.put("kind", it.getKind());
                m.put("questionId", qid == null ? null : String.valueOf(qid));
                m.put("stem", qid == null ? null : stemById.get(qid));
                m.put("blockJson", qid == null ? null : parseJson(blockById.get(qid)));
                // 审核置信度（>=90 可速过 / 60-89 常规 / <60 重点审；NULL=未评）
                m.put("confidence", it.getConfidence());
            }
            out.add(m);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("page", page);
        r.put("items", out);
        return r;
    }

    // ───────────────── 页级确认 ─────────────────

    /** 页级确认（upsert biz_review_page，reviewed=1 + 审计列）。 */
    public Map<String, Object> confirmPage(Long bookId, Integer page) {
        requireBookId(bookId);
        if (page == null || page < 1) {
            throw new ServiceException("page 非法（需 >=1）", 400);
        }
        Long uid = LoginHelper.getUserId();
        Date now = new Date();
        BizReviewPage existing = reviewPageMapper.selectOne(new LambdaQueryWrapper<BizReviewPage>()
            .eq(BizReviewPage::getBookId, bookId)
            .eq(BizReviewPage::getPageNo, page)
            .last("LIMIT 1"));
        if (existing == null) {
            BizReviewPage row = new BizReviewPage();
            row.setBookId(bookId);
            row.setPageNo(page);
            row.setReviewed(1);
            row.setReviewedBy(uid);
            row.setReviewedTime(now);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            reviewPageMapper.insert(row);
            existing = row;
        } else {
            existing.setReviewed(1);
            existing.setReviewedBy(uid);
            existing.setReviewedTime(now);
            existing.setUpdateTime(now);
            reviewPageMapper.updateById(existing);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(existing.getId()));
        r.put("bookId", String.valueOf(bookId));
        r.put("page", page);
        r.put("reviewed", 1);
        return r;
    }

    // ───────────────── 问题登记 CRUD ─────────────────

    /** 登记问题。返回 {id}。 */
    public Map<String, Object> createIssue(Long bookId, Long questionId, Integer sourcePage,
                                           String issueType, String description) {
        requireBookId(bookId);
        Date now = new Date();
        BizReviewIssue row = new BizReviewIssue();
        row.setBookId(bookId);
        row.setQuestionId(questionId);
        row.setSourcePage(sourcePage);
        row.setIssueType(issueType);
        row.setDescription(description);
        row.setStatus(ISSUE_STATUS_DEFAULT);
        row.setCreateBy(LoginHelper.getUserId());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        reviewIssueMapper.insert(row);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(row.getId()));
        return r;
    }

    /** 问题列表（bookId 必填，type/status 可选筛选），按 create_time 倒序。 */
    public List<Map<String, Object>> listIssues(Long bookId, String type, String status) {
        requireBookId(bookId);
        List<BizReviewIssue> rows = reviewIssueMapper.selectList(new LambdaQueryWrapper<BizReviewIssue>()
            .eq(BizReviewIssue::getBookId, bookId)
            .eq(StringUtils.isNotBlank(type), BizReviewIssue::getIssueType, type)
            .eq(StringUtils.isNotBlank(status), BizReviewIssue::getStatus, status)
            .orderByDesc(BizReviewIssue::getCreateTime)
            .orderByDesc(BizReviewIssue::getId));
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (BizReviewIssue it : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(it.getId()));
            m.put("bookId", String.valueOf(it.getBookId()));
            m.put("questionId", it.getQuestionId() == null ? null : String.valueOf(it.getQuestionId()));
            m.put("sourcePage", it.getSourcePage());
            m.put("issueType", it.getIssueType());
            m.put("description", it.getDescription());
            m.put("status", it.getStatus());
            m.put("createBy", it.getCreateBy() == null ? null : String.valueOf(it.getCreateBy()));
            m.put("createTime", it.getCreateTime());
            out.add(m);
        }
        return out;
    }

    /** 更新问题状态（待处理 / 已改 / 搁置）。 */
    public Map<String, Object> updateIssueStatus(Long id, String status) {
        if (id == null) {
            throw new ServiceException("id 必填", 400);
        }
        if (StringUtils.isBlank(status)) {
            throw new ServiceException("status 必填", 400);
        }
        BizReviewIssue row = reviewIssueMapper.selectById(id);
        if (row == null) {
            throw new ServiceException("问题不存在：" + id, 404);
        }
        row.setStatus(status);
        row.setUpdateTime(new Date());
        reviewIssueMapper.updateById(row);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        r.put("status", status);
        return r;
    }

    // ───────────────── helpers ─────────────────

    private void requireBookId(Long bookId) {
        if (bookId == null) {
            throw new ServiceException("bookId 必填", 400);
        }
    }

    /** JSON 字符串 → 解析对象（供 envelope 直出结构）；解析失败/空返原值。 */
    private Object parseJson(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
