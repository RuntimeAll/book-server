package org.dromara.book.service.shelf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizQuestionBlock;
import org.dromara.book.domain.entity.BizQuestionImage;
import org.dromara.book.domain.entity.BizShelfBook;
import org.dromara.book.domain.entity.BizShelfItem;
import org.dromara.book.domain.entity.BizShelfNode;
import org.dromara.book.domain.entity.BizTextContent;
import org.dromara.book.mapper.BizQuestionBlockMapper;
import org.dromara.book.mapper.BizQuestionImageMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizShelfBookMapper;
import org.dromara.book.mapper.BizShelfItemMapper;
import org.dromara.book.mapper.BizShelfNodeMapper;
import org.dromara.book.mapper.BizTextContentMapper;
import org.dromara.book.service.OssImageFetcher;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 整书导出 PDF 服务（POST /teacher/shelf/book/{bookId}/export）。
 *
 * <p>两型分路（架构照抄 {@link org.dromara.book.service.SpecialExportService} 的
 * Chrome 调起/超时守卫/OSS/防注入基建，另立主题 {@code export-themes/book-v1}）：
 * <ul>
 *   <li><b>讲义/练习册书</b>（book_type=lecture/workbook）：分讲渲染（每个顶层节点一份
 *       HTML → 无头 Chrome → PDF，60s/讲超时）+ pdfbox 合并；书头生成扉页（书名+讲目录）。
 *       题以 blockJson <b>整块</b>喂主题（text/image/option 三型渲染，🔴 修专项 mapping
 *       只抽文本丢 blockJson 图的缺口——本服务不复制那段）；withAnswers=true 时题后渲
 *       【答案】区（answer_text_content 经 biz_question.answer_text_content_id 取，无答案跳过）。</li>
 *   <li><b>图片书</b>（book_type=textbook）：不走 HTML——按 structure seq 遍历题 blockJson
 *       的 image url（回落 biz_question_image role=stem），OssImageFetcher 取字节，
 *       OpenPDF 整页图逐页拼 A4（等比满页），零渲染风险。</li>
 * </ul>
 *
 * <p>鉴权：登录 + book owner 或超管（与 structure 同门禁语义；超管放行供审阅/代导）。
 * 只读导出：不回写 used_count（与专项导出不同——整书导出非"拿去上课"信号）。
 *
 * @author backend-dev PRD 课时绑书章节+整书导出（2026-07-15）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookExportService {

    private final BizShelfBookMapper bookMapper;
    private final BizShelfNodeMapper nodeMapper;
    private final BizShelfItemMapper itemMapper;
    private final BizQuestionMapper questionMapper;
    private final BizQuestionBlockMapper blockMapper;
    private final BizTextContentMapper textContentMapper;
    private final BizQuestionImageMapper questionImageMapper;
    private final OssImageFetcher ossImageFetcher;
    private final ObjectMapper om;

    private static final String THEME_PREFIX = "export-themes/book-v1/";
    private static final String THEME_HTML = "book-v1.html";
    private static final String DATA_TOKEN = "__PAPER_DATA__";

    private static final String[] CHROME_CANDIDATES = {
        "C:/Program Files/Google/Chrome/Application/chrome.exe",
        "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
        System.getenv("LOCALAPPDATA") + "/Google/Chrome/Application/chrome.exe",
        "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
        "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
        "/usr/bin/google-chrome",
        "/usr/bin/chromium-browser",
        "/usr/bin/chromium"
    };

    /** 每讲 60s（与专项导出同守卫；整书按讲分片，单片超时不吃满全书）。 */
    private static final long CHROME_TIMEOUT_MS = 60_000L;

    // ───────────────── 导出主流程 ─────────────────

    /**
     * 整书导出。
     *
     * @param bookId 书 id
     * @param body   {withAnswers?:boolean}
     * @return {url, pages, bookId, bookType}
     */
    public Map<String, Object> export(Long bookId, Map<String, Object> body) {
        BizShelfBook book = requireExportableBook(bookId);
        boolean withAnswers = truthy(body.get("withAnswers"));

        byte[] pdf;
        if ("textbook".equals(book.getBookType())) {
            pdf = exportImageBook(book);
        } else {
            pdf = exportLectureBook(book, withAnswers);
        }

        int pages = countPages(pdf);
        OssClient oss = OssFactory.instance();
        UploadResult up = oss.uploadSuffix(pdf, ".pdf", "application/pdf");
        log.info("[book-export] bookId={} type={} pages={} size={}B url={}",
            bookId, book.getBookType(), pages, pdf.length, up.getUrl());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("bookType", book.getBookType());
        r.put("pages", pages);
        r.put("url", up.getUrl());
        return r;
    }

    // ───────────────── 讲义/练习册书：分讲 Chrome 渲染 + pdfbox 合并 ─────────────────

    private byte[] exportLectureBook(BizShelfBook book, boolean withAnswers) {
        List<BizShelfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, book.getId())
            .orderByAsc(BizShelfNode::getSeq).orderByAsc(BizShelfNode::getId));
        List<BizShelfItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizShelfItem>()
            .eq(BizShelfItem::getBookId, book.getId())
            .orderByAsc(BizShelfItem::getSeq).orderByAsc(BizShelfItem::getId));
        if (nodes.isEmpty()) {
            throw new ServiceException("书无目录结构，无法导出", 400);
        }

        Map<Long, List<BizShelfNode>> childrenOf = new LinkedHashMap<>();
        List<BizShelfNode> chapters = new ArrayList<>();
        for (BizShelfNode n : nodes) {
            if (n.getParentId() == null) chapters.add(n);
            else childrenOf.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        Map<Long, List<BizShelfItem>> itemsByNode = new LinkedHashMap<>();
        Set<Long> qids = new LinkedHashSet<>();
        for (BizShelfItem it : items) {
            itemsByNode.computeIfAbsent(it.getNodeId(), k -> new ArrayList<>()).add(it);
            if ("question".equals(it.getKind()) && it.getQuestionId() != null) qids.add(it.getQuestionId());
        }
        Map<Long, String> blockJsonByQid = loadBlockJson(qids);
        Map<Long, String> answerByQid = withAnswers ? loadAnswers(qids) : Map.of();

        Path work = prepareTheme();
        List<Path> parts = new ArrayList<>();
        try {
            // 扉页（书名 + 讲目录）
            Map<String, Object> cover = new LinkedHashMap<>();
            cover.put("cover", true);
            cover.put("bookTitle", book.getTitle());
            cover.put("sub", joinMeta(book));
            List<String> toc = new ArrayList<>();
            for (BizShelfNode c : chapters) toc.add(c.getName());
            cover.put("toc", toc);
            parts.add(renderPdf(work, cover, "cover"));

            // 分讲渲染
            int idx = 0;
            for (BizShelfNode chapter : chapters) {
                idx++;
                List<Map<String, Object>> blocks = new ArrayList<>();
                buildBlocks(chapter, 1, childrenOf, itemsByNode, blockJsonByQid, answerByQid, blocks);
                if (blocks.size() <= 1) continue; // 只有标题的空讲跳过
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("bookTitle", book.getTitle());
                data.put("withAnswers", withAnswers);
                data.put("blocks", blocks);
                parts.add(renderPdf(work, data, "ch" + idx));
            }
            if (parts.size() <= 1) {
                throw new ServiceException("书内容为空，无可导出的讲", 400);
            }
            return mergePdfs(parts);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[book-export] 讲义书导出失败 bookId={}", book.getId(), e);
            throw new ServiceException("整书导出失败: " + e.getMessage(), 500);
        } finally {
            deleteQuietly(work);
        }
    }

    /**
     * DFS 一讲子树 → 主题 blocks（heading/explain/question）。
     *
     * <p>题号形态对齐书浏览页（2026-07-15 用户拍板）：不自造全书连续大号；题干行首若自带原书
     * 小题号 {@code N．}/{@code N.}（全角顿点恒剥、半角句点负前瞻挡小数）就提出来当题号、正文去号；
     * 无自带号的（如【典型例题】开头）题号位留空、绝不编造。剥号只动题面首个 text 块，只剥一次。
     */
    private void buildBlocks(BizShelfNode node, int level,
                             Map<Long, List<BizShelfNode>> childrenOf,
                             Map<Long, List<BizShelfItem>> itemsByNode,
                             Map<Long, String> blockJsonByQid,
                             Map<Long, String> answerByQid,
                             List<Map<String, Object>> out) {
        Map<String, Object> heading = new LinkedHashMap<>();
        heading.put("type", "heading");
        heading.put("level", Math.min(level, 3));
        heading.put("text", node.getName());
        out.add(heading);

        for (BizShelfItem it : itemsByNode.getOrDefault(node.getId(), List.of())) {
            if ("explain".equals(it.getKind())) {
                Map<String, Object> ex = parseObjMap(it.getExplainJson());
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("type", "explain");
                b.put("title", ex.get("title"));
                b.put("text", ex.get("text"));
                out.add(b);
            } else if ("question".equals(it.getKind()) && it.getQuestionId() != null) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("type", "question");
                // 🔴 不自造题号：题干行首自带小号则提出来当题号（正文去号），无号则题号位留空
                Map<String, Object> ov = parseObjMap(it.getOverrideJson());
                Object ovStem = ov.get("stem");
                String blockJson = blockJsonByQid.get(it.getQuestionId());
                if (ovStem != null && !String.valueOf(ovStem).isBlank()) {
                    // 书内改题副本：以 override 题面为准（不回落源 blockJson，防新题面配旧图/旧选项）
                    String[] lifted = liftLeadingNo(String.valueOf(ovStem));
                    if (lifted[0] != null) b.put("no", lifted[0]);
                    b.put("stem", lifted[1]);
                } else if (blockJson != null && !blockJson.isBlank()) {
                    // 🔴 blockJson 整块喂主题（rows 原样传递，text/image/option 三型全保真，不丢图）
                    Object rows = rowsOf(blockJson);
                    if (rows != null) {
                        String liftedNo = liftNoFromRows(rows);
                        if (liftedNo != null) b.put("no", liftedNo);
                        b.put("rows", rows);
                    } else {
                        b.put("stem", "（题面解析失败 qid=" + it.getQuestionId() + "）");
                    }
                } else {
                    String[] lifted = liftLeadingNo(stemFallback(it.getQuestionId()));
                    if (lifted[0] != null) b.put("no", lifted[0]);
                    b.put("stem", lifted[1]);
                }
                String ans = answerByQid.get(it.getQuestionId());
                if (ans != null && !ans.isBlank() && ovStem == null) b.put("answer", ans);
                // 角色三键透传（教材配套书迁移后 override_json 携 role/roleLabel/roleSeq）：主题按此渲染
                //   样式化前缀【典型例题1】/【对应练习2】，与源书视觉一致；无 roleLabel 的题不加前缀。
                Object roleLabel = ov.get("roleLabel");
                if (roleLabel != null && !String.valueOf(roleLabel).isBlank()) {
                    b.put("role", ov.get("role"));
                    b.put("roleLabel", roleLabel);
                    b.put("roleSeq", ov.get("roleSeq"));
                }
                out.add(b);
            }
        }
        for (BizShelfNode child : childrenOf.getOrDefault(node.getId(), List.of())) {
            buildBlocks(child, level + 1, childrenOf, itemsByNode, blockJsonByQid, answerByQid, out);
        }
    }

    /**
     * 从行首剥一次原书小题号（与 book-ui liftLeadingNo 同款）：
     * 全角顿点 {@code N．} 恒剥；半角句点 {@code N.} 仅当其后不紧跟数字才剥（挡 3.14 小数）。
     *
     * @return {号, 剩余}；未命中返回 {null, 原文}。
     */
    private static final java.util.regex.Pattern LEADING_NO =
        java.util.regex.Pattern.compile("^[ \\t\\u00a0]*(\\d{1,3})(?:\\uff0e|\\.(?!\\d))[ \\t\\u00a0]*");

    private String[] liftLeadingNo(String text) {
        String s = text == null ? "" : text;
        java.util.regex.Matcher m = LEADING_NO.matcher(s);
        if (m.find()) {
            return new String[]{m.group(1), s.substring(m.end())};
        }
        return new String[]{null, s};
    }

    /**
     * 在 rows 里剥号：只动**文档序第一个 text 块**（= 题干开头），命中则就地去号并返回该号；
     * 首个 text 块无号 → 返回 null（不继续找、不编造），对齐 book-ui liftFromBlockDoc。
     */
    @SuppressWarnings("unchecked")
    private String liftNoFromRows(Object rows) {
        if (!(rows instanceof List)) return null;
        for (Object rowObj : (List<Object>) rows) {
            if (!(rowObj instanceof Map)) continue;
            Object cellsObj = ((Map<String, Object>) rowObj).get("cells");
            if (!(cellsObj instanceof List)) continue;
            for (Object cellObj : (List<Object>) cellsObj) {
                if (!(cellObj instanceof Map)) continue;
                Map<String, Object> cell = (Map<String, Object>) cellObj;
                if ("text".equals(cell.get("type"))) {
                    Object md = cell.get("md");
                    String[] lifted = liftLeadingNo(md == null ? "" : String.valueOf(md));
                    if (lifted[0] == null) return null; // 首个 text 块无号 → 不剥、不编造
                    cell.put("md", lifted[1]);
                    return lifted[0];
                }
            }
        }
        return null;
    }

    /** blockJson → rows（Jackson 树 → 普通 List/Map，随 __PAPER_DATA__ 序列化进主题）。 */
    private Object rowsOf(String blockJson) {
        try {
            JsonNode root = om.readTree(blockJson);
            JsonNode rows = root.path("rows");
            if (!rows.isArray()) return null;
            return om.convertValue(rows, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 无 blockJson 老题回落 stem_text。 */
    private String stemFallback(Long qid) {
        try {
            BizQuestion q = questionMapper.selectById(qid);
            if (q != null && q.getStemText() != null && !q.getStemText().isBlank()) return q.getStemText();
        } catch (Exception ignore) {
        }
        return "（题干缺失 qid=" + qid + "）";
    }

    private Map<Long, String> loadBlockJson(Set<Long> qids) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (qids.isEmpty()) return out;
        List<BizQuestionBlock> blocks = blockMapper.selectBatchIds(qids);
        if (blocks != null) {
            for (BizQuestionBlock b : blocks) out.put(b.getQuestionId(), b.getBlockJson());
        }
        return out;
    }

    /** 答案文本批量取：biz_question.answer_text_content_id → biz_text_content.content。 */
    private Map<Long, String> loadAnswers(Set<Long> qids) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (qids.isEmpty()) return out;
        List<BizQuestion> questions = questionMapper.selectBatchIds(qids);
        if (questions == null) return out;
        Map<Long, Long> tcIdByQid = new LinkedHashMap<>();
        for (BizQuestion q : questions) {
            if (q.getAnswerTextContentId() != null) tcIdByQid.put(q.getId(), q.getAnswerTextContentId());
        }
        if (tcIdByQid.isEmpty()) return out;
        List<BizTextContent> tcs = textContentMapper.selectBatchIds(new LinkedHashSet<>(tcIdByQid.values()));
        Map<Long, String> contentById = new LinkedHashMap<>();
        if (tcs != null) {
            for (BizTextContent t : tcs) contentById.put(t.getId(), t.getContent());
        }
        for (Map.Entry<Long, Long> e : tcIdByQid.entrySet()) {
            String c = contentById.get(e.getValue());
            if (c != null && !c.isBlank()) out.put(e.getKey(), c);
        }
        return out;
    }

    // ───────────────── 电子课本阅读页数据（目录+页码+整页图） ─────────────────

    /**
     * 电子课本阅读页数据（2026-07-30 用户点单：课本展示=目录→页码→整页图翻书，不再走题块浏览页）。
     *
     * <p>零迁移：页图仍取自题块（每页=一条题引用，blockJson 首图 / biz_question_image role=stem 兜底，
     * 与 {@link #exportImageBook} 同口径）；页码 = {@code biz_shelf_item.source_page}（PRD-006 录入
     * 审核链已回填），缺失回退文档序号。章 = 顶层节点，startPage = 该章子树最小页码。
     *
     * @return {bookId, title, totalPages, chapters:[{nodeId,name,startPage}], pages:[{page,url}]}
     */
    public Map<String, Object> textbookPages(Long bookId) {
        BizShelfBook book = requireExportableBook(bookId);

        List<BizShelfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, bookId)
            .orderByAsc(BizShelfNode::getSeq).orderByAsc(BizShelfNode::getId));
        List<BizShelfItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizShelfItem>()
            .eq(BizShelfItem::getBookId, bookId)
            .orderByAsc(BizShelfItem::getSeq).orderByAsc(BizShelfItem::getId));

        Map<Long, List<BizShelfNode>> childrenOf = new LinkedHashMap<>();
        List<BizShelfNode> roots = new ArrayList<>();
        for (BizShelfNode n : nodes) {
            if (n.getParentId() == null) roots.add(n);
            else childrenOf.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        Map<Long, List<BizShelfItem>> itemsByNode = new LinkedHashMap<>();
        Set<Long> qids = new LinkedHashSet<>();
        for (BizShelfItem it : items) {
            itemsByNode.computeIfAbsent(it.getNodeId(), k -> new ArrayList<>()).add(it);
            if ("question".equals(it.getKind()) && it.getQuestionId() != null) qids.add(it.getQuestionId());
        }
        Map<Long, String> blockJsonByQid = loadBlockJson(qids);

        // 章=顶层节点子树；页去重取首图（同页多题只留第一张，与整书导出的页序一致）
        Map<Integer, String> urlByPage = new java.util.TreeMap<>();
        List<Map<String, Object>> chapters = new ArrayList<>();
        int running = 0;
        for (BizShelfNode root : roots) {
            List<BizShelfNode> subtree = new ArrayList<>();
            expandNodes(List.of(root), childrenOf, subtree);
            Integer chapterStart = null;
            for (BizShelfNode n : subtree) {
                for (BizShelfItem it : itemsByNode.getOrDefault(n.getId(), List.of())) {
                    if (!"question".equals(it.getKind()) || it.getQuestionId() == null) continue;
                    running++;
                    int page = it.getSourcePage() != null && it.getSourcePage() > 0 ? it.getSourcePage() : running;
                    String url = firstImageUrl(blockJsonByQid.get(it.getQuestionId()));
                    if (url == null) url = stemImageFallback(it.getQuestionId());
                    if (url == null) {
                        log.warn("[textbook-pages] 页无图 bookId={} qid={} page={}", bookId, it.getQuestionId(), page);
                        continue;
                    }
                    urlByPage.putIfAbsent(page, url);
                    if (chapterStart == null || page < chapterStart) chapterStart = page;
                }
            }
            if (chapterStart != null) {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("nodeId", String.valueOf(root.getId()));
                ch.put("name", root.getName());
                ch.put("startPage", chapterStart);
                chapters.add(ch);
            }
        }
        if (urlByPage.isEmpty()) {
            throw new ServiceException("本书没有整页图（不是电子课本或页图缺失）", 400);
        }

        List<Map<String, Object>> pages = new ArrayList<>(urlByPage.size());
        for (Map.Entry<Integer, String> e : urlByPage.entrySet()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("page", e.getKey());
            p.put("url", e.getValue());
            pages.add(p);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("title", book.getTitle());
        r.put("totalPages", ((java.util.TreeMap<Integer, String>) urlByPage).lastKey());
        r.put("chapters", chapters);
        r.put("pages", pages);
        return r;
    }

    // ───────────────── 图片书（textbook）：整页图逐页拼 A4 ─────────────────

    private byte[] exportImageBook(BizShelfBook book) {
        List<BizShelfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, book.getId())
            .orderByAsc(BizShelfNode::getSeq).orderByAsc(BizShelfNode::getId));
        List<BizShelfItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizShelfItem>()
            .eq(BizShelfItem::getBookId, book.getId())
            .orderByAsc(BizShelfItem::getSeq).orderByAsc(BizShelfItem::getId));

        // structure seq 顺序：按节点树 DFS，节点内按 item seq
        Map<Long, List<BizShelfNode>> childrenOf = new LinkedHashMap<>();
        List<BizShelfNode> roots = new ArrayList<>();
        for (BizShelfNode n : nodes) {
            if (n.getParentId() == null) roots.add(n);
            else childrenOf.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        Map<Long, List<BizShelfItem>> itemsByNode = new LinkedHashMap<>();
        Set<Long> qids = new LinkedHashSet<>();
        for (BizShelfItem it : items) {
            itemsByNode.computeIfAbsent(it.getNodeId(), k -> new ArrayList<>()).add(it);
            if ("question".equals(it.getKind()) && it.getQuestionId() != null) qids.add(it.getQuestionId());
        }
        Map<Long, String> blockJsonByQid = loadBlockJson(qids);

        List<Long> orderedQids = new ArrayList<>();
        // DFS 保序（roots 已按 seq 排；展开成文档序节点列）
        List<BizShelfNode> ordered = new ArrayList<>();
        expandNodes(roots, childrenOf, ordered);
        for (BizShelfNode n : ordered) {
            for (BizShelfItem it : itemsByNode.getOrDefault(n.getId(), List.of())) {
                if ("question".equals(it.getKind()) && it.getQuestionId() != null) {
                    orderedQids.add(it.getQuestionId());
                }
            }
        }
        if (orderedQids.isEmpty()) {
            throw new ServiceException("图片书无页（无题引用），无法导出", 400);
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document docu = new Document(PageSize.A4, 0, 0, 0, 0);
            PdfWriter.getInstance(docu, baos);
            docu.open();
            int added = 0;
            for (Long qid : orderedQids) {
                String url = firstImageUrl(blockJsonByQid.get(qid));
                if (url == null) url = stemImageFallback(qid);
                if (url == null || !ossImageFetcher.isWhitelisted(url)) {
                    log.warn("[book-export] 图片书页无图或非白名单 qid={} url={}", qid, url);
                    continue;
                }
                byte[] bytes = ossImageFetcher.fetch(url).bytes();
                Image img = Image.getInstance(bytes);
                img.scaleToFit(PageSize.A4.getWidth(), PageSize.A4.getHeight());
                img.setAbsolutePosition(
                    (PageSize.A4.getWidth() - img.getScaledWidth()) / 2,
                    (PageSize.A4.getHeight() - img.getScaledHeight()) / 2);
                if (added > 0) docu.newPage();
                docu.add(img);
                added++;
            }
            if (added == 0) {
                throw new ServiceException("图片书取图全部失败，无页可导出", 500);
            }
            docu.close();
            return baos.toByteArray();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[book-export] 图片书导出失败 bookId={}", book.getId(), e);
            throw new ServiceException("图片书导出失败: " + e.getMessage(), 500);
        }
    }

    private void expandNodes(List<BizShelfNode> level, Map<Long, List<BizShelfNode>> childrenOf,
                             List<BizShelfNode> out) {
        for (BizShelfNode n : level) {
            out.add(n);
            List<BizShelfNode> kids = childrenOf.get(n.getId());
            if (kids != null) expandNodes(kids, childrenOf, out);
        }
    }

    /** blockJson 里第一个 image cell 的 url。 */
    private String firstImageUrl(String blockJson) {
        if (blockJson == null || blockJson.isBlank()) return null;
        try {
            JsonNode root = om.readTree(blockJson);
            for (JsonNode row : root.path("rows")) {
                for (JsonNode cell : row.path("cells")) {
                    if ("image".equals(cell.path("type").asText()) && cell.hasNonNull("url")) {
                        String u = cell.path("url").asText("");
                        if (!u.isBlank()) return u;
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /** 回落 biz_question_image role=stem 首图。 */
    private String stemImageFallback(Long qid) {
        try {
            List<BizQuestionImage> imgs = questionImageMapper.selectList(
                new LambdaQueryWrapper<BizQuestionImage>()
                    .eq(BizQuestionImage::getQuestionId, qid)
                    .eq(BizQuestionImage::getRole, "stem")
                    .orderByAsc(BizQuestionImage::getSeq));
            if (imgs != null && !imgs.isEmpty()) return imgs.get(0).getOssUrl();
        } catch (Exception ignore) {
        }
        return null;
    }

    // ───────────────── 渲染基建（Chrome / 主题 / 合并；对齐 SpecialExportService） ─────────────────

    private Path prepareTheme() {
        try {
            Path work = Files.createTempDirectory("book-export-");
            PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver();
            Resource[] res = r.getResources("classpath*:" + THEME_PREFIX + "**");
            int copied = 0;
            for (Resource rc : res) {
                if (!rc.isReadable()) continue;
                String uri = rc.getURI().toString();
                int idx = uri.indexOf(THEME_PREFIX);
                if (idx < 0) continue;
                String rel = uri.substring(idx + THEME_PREFIX.length());
                if (rel.isBlank() || rel.endsWith("/")) continue;
                Path dst = work.resolve(rel);
                Files.createDirectories(dst.getParent());
                try (InputStream in = rc.getInputStream()) {
                    Files.copy(in, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            if (copied == 0 || !Files.exists(work.resolve(THEME_HTML))) {
                throw new ServiceException("导出主题资源缺失（export-themes/book-v1）", 500);
            }
            return work;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[book-export] 准备主题失败", e);
            throw new ServiceException("导出环境准备失败: " + e.getMessage(), 500);
        }
    }

    /** 渲一份数据 → PDF 文件（work 下 part-{tag}.pdf）。60s 守卫 + stdout 文件重定向（防 XNIO 钉死）。 */
    private Path renderPdf(Path work, Map<String, Object> data, String tag) {
        try {
            String json = om.writeValueAsString(data);
            // 防 </script> 提前闭合注入块（同 SpecialExportService）
            json = json.replace("</", "<\\/");
            String tpl = Files.readString(work.resolve(THEME_HTML), StandardCharsets.UTF_8);
            String html = tpl.replace(DATA_TOKEN, json);
            Path htmlOut = work.resolve("part-" + tag + ".html");
            Files.writeString(htmlOut, html, StandardCharsets.UTF_8);
            Path pdfOut = work.resolve("part-" + tag + ".pdf");

            String chrome = resolveChrome();
            List<String> cmd = new ArrayList<>();
            cmd.add(chrome);
            cmd.add("--headless=new");
            cmd.add("--disable-gpu");
            cmd.add("--no-sandbox");
            cmd.add("--no-pdf-header-footer");
            // 一讲含 10~20 张 OSS 网络图，虚拟时间预算放宽到 20s（守卫仍 60s 真实时间）
            cmd.add("--virtual-time-budget=20000");
            cmd.add("--run-all-compositor-stages-before-draw");
            cmd.add("--print-to-pdf=" + pdfOut.toAbsolutePath());
            cmd.add(htmlOut.toAbsolutePath().toUri().toString());

            Path logFile = work.resolve("chrome-" + tag + ".log");
            Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
            boolean done = p.waitFor(CHROME_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                throw new ServiceException("PDF 渲染超时（讲 " + tag + " 超 " + (CHROME_TIMEOUT_MS / 1000) + "s）", 500);
            }
            if (!Files.exists(pdfOut) || Files.size(pdfOut) == 0) {
                String logtxt = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
                log.error("[book-export] chrome 无产物 tag={} exit={} log={}", tag, p.exitValue(), logtxt);
                throw new ServiceException("PDF 渲染失败（讲 " + tag + " 无产物）", 500);
            }
            return pdfOut;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[book-export] 渲染异常 tag={}", tag, e);
            throw new ServiceException("PDF 渲染异常: " + e.getMessage(), 500);
        }
    }

    /** pdfbox 顺序合并（openhtmltopdf 传递引入的 pdfbox 2.0.x）。 */
    private byte[] mergePdfs(List<Path> parts) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        for (Path p : parts) {
            merger.addSource(p.toFile());
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        merger.setDestinationStream(baos);
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
        return baos.toByteArray();
    }

    private int countPages(byte[] pdf) {
        try (PDDocument d = PDDocument.load(new ByteArrayInputStream(pdf))) {
            return d.getNumberOfPages();
        } catch (Exception e) {
            log.warn("[book-export] 页数统计失败: {}", e.getMessage());
            return -1;
        }
    }

    private String resolveChrome() {
        String env = System.getenv("CHROME_BIN");
        if (env != null && !env.isBlank() && Files.exists(Path.of(env))) return env;
        for (String c : CHROME_CANDIDATES) {
            if (c == null) continue;
            try {
                if (Files.exists(Path.of(c))) return c;
            } catch (Exception ignore) {
            }
        }
        throw new ServiceException("未找到 Chrome/Edge，设 CHROME_BIN 环境变量指定", 500);
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(pp -> {
                try {
                    Files.deleteIfExists(pp);
                } catch (Exception ignore) {
                }
            });
        } catch (Exception ignore) {
        }
    }

    // ───────────────── 门禁与小工具 ─────────────────

    /** 导出门禁：书存在（404）+ owner / is_public=1 / 超管（403）。 */
    private BizShelfBook requireExportableBook(Long bookId) {
        BizShelfBook b = bookMapper.selectById(bookId);
        if (b == null) throw new ServiceException("书不存在", 404);
        Long uid = LoginHelper.getUserId();
        if (uid == null) throw new ServiceException("未登录", 401);
        boolean pub = b.getIsPublic() != null && b.getIsPublic() == 1;
        if (!pub && b.getOwnerId() != null && !uid.equals(b.getOwnerId()) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("无权导出该书", 403);
        }
        return b;
    }

    private String joinMeta(BizShelfBook b) {
        List<String> parts = new ArrayList<>();
        if (b.getGrade() != null && !b.getGrade().isBlank()) parts.add(b.getGrade());
        if (b.getEdition() != null && !b.getEdition().isBlank()) parts.add(b.getEdition());
        return String.join(" · ", parts);
    }

    private boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        return o != null && ("true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o)));
    }

    private Map<String, Object> parseObjMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> m = JsonUtils.parseMap(json);
            return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
