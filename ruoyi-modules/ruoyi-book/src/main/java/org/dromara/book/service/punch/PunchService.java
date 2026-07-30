package org.dromara.book.service.punch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizQuestionBlock;
import org.dromara.book.domain.entity.BizShelfBook;
import org.dromara.book.domain.entity.BizShelfItem;
import org.dromara.book.domain.entity.BizShelfNode;
import org.dromara.book.domain.entity.BizTextContent;
import org.dromara.book.mapper.BizQuestionBlockMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizShelfBookMapper;
import org.dromara.book.mapper.BizShelfItemMapper;
import org.dromara.book.mapper.BizShelfNodeMapper;
import org.dromara.book.mapper.BizTextContentMapper;
import org.dromara.book.service.ChromePdfRenderer;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每日打卡书服务（PRD-013 批1）：打卡内容 = 一本有结构的书（{@code biz_shelf_book}，book_type=daily_punch）。
 *
 * <p>结构映射（PRD-013 D5，已定死）：
 * <ul>
 *   <li><b>天 = 节点</b> {@code biz_shelf_node}：nodeType={@code punch_day}、name={@code 第N天}、seq={@code day}、
 *       meta_json={@code {goals:[...], review:{status,issues:[...]}}}</li>
 *   <li><b>计算模块 = item</b>：kind={@code module}、content_json={@code {type:oral|vertical|stepwise,title,items:[{q,a}]}}、
 *       seq=模块序（出题器现产，本书私有数据）</li>
 *   <li><b>轮换位真题 = 逐题 item</b>：kind={@code question} + question_id 引用，seq 续排 ——
 *       🔴 <b>绝不复制题面文本</b>：渲染时现取 {@code biz_question_block.block_json}，改题库即改打卡书</li>
 * </ul>
 *
 * <p>展示与导出同源（D3/D9）：{@link #assembleDay} 组的同一份 data，
 * 预览走 {@link ChromePdfRenderer#renderHtml}（返 HTML 串给 FE iframe）、
 * 导出走 {@link ChromePdfRenderer#render}（同主题渲 PDF）——没有第二套渲染。
 *
 * <p>书 style_meta_json：{@code {accent,subtitle,variantName,rotatingTitle,punchReview}}。
 *
 * @author backend-dev (PRD-013 批1-BE)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PunchService {

    private final BizShelfBookMapper bookMapper;
    private final BizShelfNodeMapper nodeMapper;
    private final BizShelfItemMapper itemMapper;
    private final BizQuestionMapper questionMapper;
    private final BizQuestionBlockMapper blockMapper;
    private final BizTextContentMapper textContentMapper;
    private final ChromePdfRenderer renderer;
    private final ObjectMapper om;

    /** 打卡纸面主题（版式=每日一练定版 CSS，单文件自包含）。 */
    public static final String THEME = "punch-v1";

    /** 天节点 nodeType（D5 定死）。 */
    public static final String NODE_TYPE = "punch_day";

    private static final String KIND_MODULE = "module";
    private static final String KIND_QUESTION = "question";
    private static final String TYPE_ROTATING = "rotating";

    private static final String DEFAULT_ACCENT = "暑假作业";
    private static final String DEFAULT_ROTATING_TITLE = "解决问题";

    // ───────────────── ① 组数据（展示/导出同源） ─────────────────

    /**
     * 组一天的模板数据（punch-v1 主题入参）。
     *
     * @param bookId 打卡书 id
     * @param day    第几天（= 天节点 seq）
     * @param paper  {@code question} 题目卷 / {@code answer} 解析卷
     * @return {paper,title,accent,subtitle,variantName,day,goals,modules}
     */
    public Map<String, Object> assembleDay(Long bookId, int day, String paper) {
        BizShelfBook book = requireReadableBook(bookId);
        BizShelfNode node = requireDay(bookId, day);
        Map<String, Object> meta = parseMap(node.getMetaJson());
        Map<String, Object> style = parseMap(book.getStyleMetaJson());

        List<BizShelfItem> items = itemsOf(node.getId());
        List<Map<String, Object>> modules = new ArrayList<>();
        List<Long> qids = new ArrayList<>();
        for (BizShelfItem it : items) {
            if (KIND_MODULE.equals(it.getKind())) {
                Map<String, Object> m = parseMap(it.getContentJson());
                if (m.isEmpty()) {
                    log.warn("[punch] 模块 content_json 为空 itemId={} bookId={} day={}", it.getId(), bookId, day);
                    continue;
                }
                m.put("itemId", String.valueOf(it.getId()));   // FP11a 就地改字定位用（不进纸面文本）
                modules.add(m);
            } else if (KIND_QUESTION.equals(it.getKind()) && it.getQuestionId() != null) {
                qids.add(it.getQuestionId());
            }
        }
        // 🔴 轮换位恒在末尾：题库题引用现取现渲（题面唯一源=block_json）
        if (!qids.isEmpty()) {
            modules.add(rotatingModule(qids, meta, style));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        boolean isAnswer = "answer".equals(paper);
        // 🔴 G5 数据层剥离（verifier B1）：题目卷注入的 __PAPER_DATA__ 不携带任何答案字段——
        //    否则"查看源代码"即得全卷答案；解析卷才带。剥的是注入数据副本，DB 原样。
        if (!isAnswer) {
            for (Map<String, Object> m : modules) {
                Object modItems = m.get("items");
                if (modItems instanceof List<?> il) {
                    for (Object o : il) {
                        if (o instanceof Map<?, ?> im) {
                            ((Map<String, Object>) im).remove("a");
                            ((Map<String, Object>) im).remove("steps");
                        }
                    }
                }
                Object rotBlocks = m.get("blocks");
                if (rotBlocks instanceof List<?> bl) {
                    for (Object o : bl) {
                        if (o instanceof Map<?, ?> bm) {
                            ((Map<String, Object>) bm).remove("answer");
                        }
                    }
                }
            }
        }
        data.put("paper", isAnswer ? "answer" : "question");
        data.put("title", book.getTitle());
        data.put("accent", str(style.get("accent"), DEFAULT_ACCENT));
        data.put("subtitle", str(style.get("subtitle"), ""));
        data.put("variantName", str(style.get("variantName"), ""));
        data.put("day", day);
        data.put("goals", goalsOf(meta));
        data.put("modules", modules);
        return data;
    }

    /** 轮换位模块：{type:rotating,title,blocks:[{qid,rows,answer}]}（题面绝不复制成文本）。 */
    private Map<String, Object> rotatingModule(List<Long> qids, Map<String, Object> meta, Map<String, Object> style) {
        Set<Long> qidSet = new LinkedHashSet<>(qids);
        Map<Long, String> blockJsonByQid = loadBlockJson(qidSet);
        Map<Long, String> answerByQid = loadAnswers(qidSet);

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Long qid : qids) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("qid", String.valueOf(qid));                 // 🔴 字符串（雪花号 JSON double 会截尾）
            Object rows = rowsOf(blockJsonByQid.get(qid));
            if (rows == null) {
                log.warn("[punch] 轮换位题无 blockJson qid={}", qid);
                b.put("rows", List.of(Map.of("cells",
                    List.of(Map.of("type", "text", "md", "（题面缺失 qid=" + qid + "）")))));
            } else {
                stripLeadingNo(rows);   // 源书小题号剥掉：卷面题号由主题按顺序编（防 "1．12．"）
                b.put("rows", rows);
            }
            String ans = answerByQid.get(qid);
            if (ans != null && !ans.isBlank()) b.put("answer", ans);
            blocks.add(b);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", TYPE_ROTATING);
        m.put("title", rotatingTitle(meta, style));
        m.put("blocks", blocks);
        return m;
    }

    /** 轮换位标题：天节点 meta.rotatingTitle → 书 style_meta.rotatingTitle → 缺省「解决问题」。 */
    private String rotatingTitle(Map<String, Object> meta, Map<String, Object> style) {
        String t = str(meta.get("rotatingTitle"), "");
        if (!t.isEmpty()) return t;
        return str(style.get("rotatingTitle"), DEFAULT_ROTATING_TITLE);
    }

    // ───────────────── ② 预览 / 导出（同一份 data，两个出口） ─────────────────

    /** 预览 HTML 串（FE iframe 用；与导出 PDF 同 theme+data，构造上 1:1）。 */
    public String previewHtml(Long bookId, int day, String paper) {
        return renderer.renderHtml(THEME, assembleDay(bookId, day, paper));
    }

    /**
     * 导出本天 PDF → OSS。
     *
     * @param papers 要出的卷（question/answer），空 = 双卷
     * @return {questionUrl?, answerUrl?}
     */
    public Map<String, Object> exportDay(Long bookId, int day, List<String> papers) {
        List<String> want = new ArrayList<>();
        if (papers != null) {
            for (Object o : papers) {
                String s = String.valueOf(o);
                if ("question".equals(s) || "answer".equals(s)) want.add(s);
            }
        }
        if (want.isEmpty()) {
            want.add("question");
            want.add("answer");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("day", day);
        for (String paper : want) {
            byte[] pdf = renderer.render(THEME, assembleDay(bookId, day, paper), "punch-d" + day + "-" + paper);
            pdf = trimBlankTailPages(pdf);
            OssClient oss = OssFactory.instance();
            UploadResult up = oss.uploadSuffix(pdf, ".pdf", "application/pdf");
            r.put("question".equals(paper) ? "questionUrl" : "answerUrl", up.getUrl());
            log.info("[punch] 导出 bookId={} day={} paper={} size={}B url={}", bookId, day, paper, pdf.length, up.getUrl());
        }
        return r;
    }

    /**
     * 裁掉尾部空白页：版面留白微溢出 297mm 时 Chrome 会多出一页。punch-v1 页脚是
     * {@code position:fixed}（每页重复），故溢出页上仍有「第 N 天/玉米练习册」文本——
     * 空白判定=剥去页脚词后无任何文本。只从末页向前裁，至少保留 1 页；失败按原样返回不阻塞导出。
     */
    private byte[] trimBlankTailPages(byte[] pdf) {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            boolean changed = false;
            while (doc.getNumberOfPages() > 1) {
                int last = doc.getNumberOfPages();
                stripper.setStartPage(last);
                stripper.setEndPage(last);
                String residue = stripper.getText(doc)
                    .replaceAll("\\s+", "")
                    .replaceAll("第\\d+天(·解析)?", "")
                    .replace("玉米练习册", "");
                if (!residue.isEmpty()) break;
                doc.removePage(last - 1);
                changed = true;
            }
            if (!changed) return pdf;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("[punch] 尾页裁剪失败，按原 PDF 返回", e);
            return pdf;
        }
    }

    // ───────────────── ③ 录入：幂等 upsert 一天 ─────────────────

    /**
     * 幂等建/覆盖「第 N 天」（AC1/G1）：同 (book,day) 重调覆盖，节点不重复建。
     *
     * <p>modules 入参：计算模块 {@code {type,title,items:[{q,a}]}} 原样落 content_json；
     * 轮换位 {@code {type:'rotating',title?,qids:[字符串数组]}} 逐 qid 落 kind=question 引用项。
     *
     * @return {nodeId, itemIds:[...]}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upsertDay(Long bookId, int day, List<String> goals, List<Map<String, Object>> modules) {
        requireOwnedBook(bookId);
        if (day < 1) throw new ServiceException("day 必须 ≥ 1", 400);

        BizShelfNode node = findDay(bookId, day);
        Map<String, Object> meta = node == null ? new LinkedHashMap<>() : parseMap(node.getMetaJson());

        // goals 覆盖；review 重置 pending（🔴 保留已有 issues 数组：重灌不抹审核痕迹）
        List<String> goalList = new ArrayList<>();
        if (goals != null) {
            for (String g : goals) {
                if (g != null && !g.isBlank()) goalList.add(g);
            }
        }
        meta.put("goals", goalList);
        Map<String, Object> review = reviewOf(meta);
        review.put("status", "pending");
        meta.put("review", review);

        // 轮换位标题（可选）随天走，缺省回落书级 style_meta
        String rotTitle = null;
        if (modules != null) {
            for (Map<String, Object> m : modules) {
                if (m != null && TYPE_ROTATING.equals(str(m.get("type"), ""))) {
                    String t = str(m.get("title"), "");
                    if (!t.isEmpty()) rotTitle = t;
                }
            }
        }
        if (rotTitle != null) meta.put("rotatingTitle", rotTitle);

        if (node == null) {
            node = new BizShelfNode();
            node.setBookId(bookId);
            node.setParentId(null);
            node.setSeq(day);
            node.setNodeType(NODE_TYPE);
            node.setName("第" + day + "天");
            node.setMetaJson(JsonUtils.toJsonString(meta));
            nodeMapper.insert(node);
        } else {
            node.setName("第" + day + "天");
            node.setMetaJson(JsonUtils.toJsonString(meta));
            nodeMapper.updateById(node);
        }

        // 旧 items 全删重插（幂等覆盖，不做逐项 diff）
        itemMapper.delete(new LambdaQueryWrapper<BizShelfItem>().eq(BizShelfItem::getNodeId, node.getId()));

        List<String> itemIds = new ArrayList<>();
        int seq = 1;
        if (modules != null) {
            for (Map<String, Object> m : modules) {
                if (m == null || m.isEmpty()) continue;
                String type = str(m.get("type"), "");
                if (type.isEmpty()) throw new ServiceException("模块缺 type", 400);
                if (TYPE_ROTATING.equals(type)) {
                    List<Long> qids = qidsOf(m.get("qids"));
                    validateQuestionsExist(qids);   // 防空壳引用（同 ShelfService finding 4）
                    for (Long qid : qids) {
                        BizShelfItem it = new BizShelfItem();
                        it.setBookId(bookId);
                        it.setNodeId(node.getId());
                        it.setSeq(seq++);
                        it.setKind(KIND_QUESTION);
                        it.setQuestionId(qid);       // 🔴 只存引用，题面绝不复制
                        it.setUsedCount(0);
                        itemMapper.insert(it);
                        itemIds.add(String.valueOf(it.getId()));
                    }
                } else {
                    Map<String, Object> payload = new LinkedHashMap<>(m);
                    payload.remove("itemId");        // 组数据时才挂的定位键，不落库
                    BizShelfItem it = new BizShelfItem();
                    it.setBookId(bookId);
                    it.setNodeId(node.getId());
                    it.setSeq(seq++);
                    it.setKind(KIND_MODULE);
                    it.setContentJson(JsonUtils.toJsonString(payload));
                    it.setUsedCount(0);
                    itemMapper.insert(it);
                    itemIds.add(String.valueOf(it.getId()));
                }
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nodeId", String.valueOf(node.getId()));
        r.put("day", day);
        r.put("itemIds", itemIds);
        log.info("[punch] upsertDay bookId={} day={} nodeId={} items={}", bookId, day, node.getId(), itemIds.size());
        return r;
    }

    // ───────────────── ④ 读回：目录 / 一天原文 ─────────────────

    /** 目录：[{day,nodeId,itemCount,review:{status,issueCount}}]（issueCount = 未 resolved 数）。 */
    public List<Map<String, Object>> listDays(Long bookId) {
        requireReadableBook(bookId);
        List<BizShelfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, bookId)
            .eq(BizShelfNode::getNodeType, NODE_TYPE)
            .orderByAsc(BizShelfNode::getSeq).orderByAsc(BizShelfNode::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizShelfNode n : nodes) {
            Map<String, Object> meta = parseMap(n.getMetaJson());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", n.getSeq());
            row.put("nodeId", String.valueOf(n.getId()));
            row.put("name", n.getName());
            row.put("itemCount", itemMapper.selectCount(new LambdaQueryWrapper<BizShelfItem>()
                .eq(BizShelfItem::getNodeId, n.getId())));
            row.put("review", reviewBrief(meta));
            out.add(row);
        }
        return out;
    }

    /** 一天原文回读（展示页/生成器共用）：{goals,modules,review}，modules 与 upsert 入参同构（round-trip）。 */
    public Map<String, Object> getDay(Long bookId, int day) {
        BizShelfBook book = requireReadableBook(bookId);
        BizShelfNode node = requireDay(bookId, day);
        Map<String, Object> meta = parseMap(node.getMetaJson());

        List<Map<String, Object>> modules = new ArrayList<>();
        List<String> qids = new ArrayList<>();
        for (BizShelfItem it : itemsOf(node.getId())) {
            if (KIND_MODULE.equals(it.getKind())) {
                Map<String, Object> m = parseMap(it.getContentJson());
                if (m.isEmpty()) continue;
                m.put("itemId", String.valueOf(it.getId()));
                modules.add(m);
            } else if (KIND_QUESTION.equals(it.getKind()) && it.getQuestionId() != null) {
                qids.add(String.valueOf(it.getQuestionId()));
            }
        }
        if (!qids.isEmpty()) {
            Map<String, Object> rot = new LinkedHashMap<>();
            rot.put("type", TYPE_ROTATING);
            rot.put("title", rotatingTitle(meta, parseMap(book.getStyleMetaJson())));
            rot.put("qids", qids);
            modules.add(rot);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("day", day);
        r.put("nodeId", String.valueOf(node.getId()));
        r.put("goals", goalsOf(meta));
        r.put("modules", modules);
        r.put("review", reviewOf(meta));
        return r;
    }

    // ───────────────── ⑤ 人眼审核流 ─────────────────

    /**
     * 提交审核（D4/G3）。
     *
     * <p>action：
     * <ul>
     *   <li>{@code issue} —— status=issue，入参 issues <b>追加</b>进清单（缺省 resolved=false）</li>
     *   <li>{@code pass} —— status=passed；传 issues 则整体覆盖（销账后回写干净清单），不传保留原样</li>
     *   <li>{@code reopen} —— status=pending（重审）；issues 处理同 pass</li>
     * </ul>
     * pass 后重算全书：各天全 passed → 书 style_meta_json.punchReview={passed:true,at:...}。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitReview(Long bookId, int day, String action, List<Map<String, Object>> issues) {
        requireOwnedBook(bookId);
        BizShelfNode node = requireDay(bookId, day);
        Map<String, Object> meta = parseMap(node.getMetaJson());
        Map<String, Object> review = reviewOf(meta);
        List<Map<String, Object>> list = issuesOf(review);

        String act = str(action, "");
        switch (act) {
            case "issue" -> {
                review.put("status", "issue");
                for (Map<String, Object> raw : normIssues(issues)) list.add(raw);
            }
            case "pass" -> {
                review.put("status", "passed");
                if (issues != null) list = normIssues(issues);
            }
            case "reopen" -> {
                review.put("status", "pending");
                if (issues != null) list = normIssues(issues);
            }
            default -> throw new ServiceException("action 只能是 pass|issue|reopen，收到：" + action, 400);
        }
        review.put("issues", list);
        review.put("at", LocalDateTime.now().withNano(0).toString());
        meta.put("review", review);
        node.setMetaJson(JsonUtils.toJsonString(meta));
        nodeMapper.updateById(node);

        boolean allPassed = recomputeBookReview(bookId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("day", day);
        r.put("review", review);
        r.put("bookPassed", allPassed);
        log.info("[punch] review bookId={} day={} action={} issues={} allPassed={}",
            bookId, day, act, list.size(), allPassed);
        return r;
    }

    /** 全书审定标记：各天全 passed → style_meta_json.punchReview={passed,at}（🔴 只 UPDATE style_meta_json 一列）。 */
    private boolean recomputeBookReview(Long bookId) {
        List<BizShelfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, bookId).eq(BizShelfNode::getNodeType, NODE_TYPE));
        boolean allPassed = !nodes.isEmpty();
        for (BizShelfNode n : nodes) {
            Map<String, Object> review = reviewOf(parseMap(n.getMetaJson()));
            if (!"passed".equals(str(review.get("status"), "pending"))) {
                allPassed = false;
                break;
            }
        }
        BizShelfBook book = bookMapper.selectById(bookId);
        Map<String, Object> style = parseMap(book == null ? null : book.getStyleMetaJson());
        Map<String, Object> punchReview = new LinkedHashMap<>();
        punchReview.put("passed", allPassed);
        punchReview.put("at", LocalDateTime.now().withNano(0).toString());
        style.put("punchReview", punchReview);

        BizShelfBook up = new BizShelfBook();
        up.setId(bookId);
        up.setStyleMetaJson(JsonUtils.toJsonString(style));
        bookMapper.updateById(up);
        return allPassed;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normIssues(List<Map<String, Object>> issues) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (issues == null) return out;
        for (Object o : issues) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> src = (Map<String, Object>) o;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("module", src.get("module"));
            m.put("seq", src.get("seq"));
            m.put("kind", src.get("kind"));
            m.put("note", src.get("note"));
            m.put("resolved", truthy(src.get("resolved")));
            out.add(m);
        }
        return out;
    }

    /** 目录角标用：{status, issueCount}（issueCount 只数未销账的）。 */
    private Map<String, Object> reviewBrief(Map<String, Object> meta) {
        Map<String, Object> review = reviewOf(meta);
        int open = 0;
        for (Map<String, Object> i : issuesOf(review)) {
            if (!truthy(i.get("resolved"))) open++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", str(review.get("status"), "pending"));
        m.put("issueCount", open);
        return m;
    }

    // ───────────────── 题库题加载（照抄 BookExportService 模式） ─────────────────

    private Map<Long, String> loadBlockJson(Set<Long> qids) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (qids.isEmpty()) return out;
        List<BizQuestionBlock> blocks = blockMapper.selectBatchIds(qids);
        if (blocks != null) {
            for (BizQuestionBlock b : blocks) out.put(b.getQuestionId(), b.getBlockJson());
        }
        return out;
    }

    /**
     * 答案文本批量取：biz_question.answer_text_content_id → biz_text_content.content；
     * FK 空的老题回落 (question_id, content_type='A') 直查。
     */
    private Map<Long, String> loadAnswers(Set<Long> qids) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (qids.isEmpty()) return out;
        List<BizQuestion> questions = questionMapper.selectBatchIds(qids);
        Map<Long, Long> tcIdByQid = new LinkedHashMap<>();
        if (questions != null) {
            for (BizQuestion q : questions) {
                if (q.getAnswerTextContentId() != null) tcIdByQid.put(q.getId(), q.getAnswerTextContentId());
            }
        }
        if (!tcIdByQid.isEmpty()) {
            List<BizTextContent> tcs = textContentMapper.selectBatchIds(new LinkedHashSet<>(tcIdByQid.values()));
            Map<Long, String> contentById = new LinkedHashMap<>();
            if (tcs != null) {
                for (BizTextContent t : tcs) contentById.put(t.getId(), t.getContent());
            }
            for (Map.Entry<Long, Long> e : tcIdByQid.entrySet()) {
                String c = contentById.get(e.getValue());
                if (c != null && !c.isBlank()) out.put(e.getKey(), c);
            }
        }
        Set<Long> missing = new LinkedHashSet<>(qids);
        missing.removeAll(out.keySet());
        if (!missing.isEmpty()) {
            List<BizTextContent> tcs = textContentMapper.selectList(new LambdaQueryWrapper<BizTextContent>()
                .in(BizTextContent::getQuestionId, missing)
                .eq(BizTextContent::getContentType, "A"));
            if (tcs != null) {
                for (BizTextContent t : tcs) {
                    if (t.getContent() != null && !t.getContent().isBlank()) out.put(t.getQuestionId(), t.getContent());
                }
            }
        }
        return out;
    }

    /** blockJson → rows（Jackson 树 → 普通 List/Map，随 __PAPER_DATA__ 序列化进主题）。 */
    private Object rowsOf(String blockJson) {
        if (blockJson == null || blockJson.isBlank()) return null;
        try {
            JsonNode root = om.readTree(blockJson);
            JsonNode rows = root.path("rows");
            if (!rows.isArray()) return null;
            return om.convertValue(rows, Object.class);
        } catch (Exception e) {
            log.warn("[punch] blockJson 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static final java.util.regex.Pattern LEADING_NO =
        java.util.regex.Pattern.compile("^[ \\t\\u00a0]*(\\d{1,3})(?:\\uff0e|\\.(?!\\d))[ \\t\\u00a0]*");

    /**
     * 剥源书小题号（只动文档序第一个 text 块，只剥一次）：卷面题号由 punch-v1 主题按轮换位顺序编，
     * 不剥会出现「1．12．…」双号。与 BookExportService.liftNoFromRows 同一正则（半角句点负前瞻挡小数）。
     */
    @SuppressWarnings("unchecked")
    private void stripLeadingNo(Object rows) {
        if (!(rows instanceof List)) return;
        for (Object rowObj : (List<Object>) rows) {
            if (!(rowObj instanceof Map)) continue;
            Object cellsObj = ((Map<String, Object>) rowObj).get("cells");
            if (!(cellsObj instanceof List)) continue;
            for (Object cellObj : (List<Object>) cellsObj) {
                if (!(cellObj instanceof Map)) continue;
                Map<String, Object> cell = (Map<String, Object>) cellObj;
                if ("text".equals(cell.get("type"))) {
                    Object md = cell.get("md");
                    String s = md == null ? "" : String.valueOf(md);
                    java.util.regex.Matcher m = LEADING_NO.matcher(s);
                    if (m.find()) cell.put("md", s.substring(m.end()));
                    return;   // 首个 text 块处理完即止（无号也不再往后找）
                }
            }
        }
    }

    // ───────────────── 门禁与小工具 ─────────────────

    private List<BizShelfItem> itemsOf(Long nodeId) {
        return itemMapper.selectList(new LambdaQueryWrapper<BizShelfItem>()
            .eq(BizShelfItem::getNodeId, nodeId)
            .orderByAsc(BizShelfItem::getSeq).orderByAsc(BizShelfItem::getId));
    }

    private BizShelfNode findDay(Long bookId, int day) {
        List<BizShelfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, bookId)
            .eq(BizShelfNode::getNodeType, NODE_TYPE)
            .eq(BizShelfNode::getSeq, day)
            .orderByAsc(BizShelfNode::getId));
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private BizShelfNode requireDay(Long bookId, int day) {
        BizShelfNode n = findDay(bookId, day);
        if (n == null) throw new ServiceException("第 " + day + " 天不存在（book=" + bookId + "）", 404);
        return n;
    }

    /** 写门禁：owner 本人 / 超管。 */
    private BizShelfBook requireOwnedBook(Long bookId) {
        BizShelfBook b = bookMapper.selectById(bookId);
        if (b == null) throw new ServiceException("书不存在", 404);
        Long uid = LoginHelper.getUserId();
        if (uid != null && b.getOwnerId() != null && !uid.equals(b.getOwnerId()) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("无权访问该书", 403);
        }
        return b;
    }

    /** 读门禁：owner 本人 / is_public=1 / 超管。 */
    private BizShelfBook requireReadableBook(Long bookId) {
        BizShelfBook b = bookMapper.selectById(bookId);
        if (b == null) throw new ServiceException("书不存在", 404);
        if (b.getIsPublic() != null && b.getIsPublic() == 1) return b;
        Long uid = LoginHelper.getUserId();
        if (uid != null && b.getOwnerId() != null && !uid.equals(b.getOwnerId()) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("无权访问该书", 403);
        }
        return b;
    }

    /** 题引用存在性校验（同 ShelfService finding 4，防空壳引用）。 */
    private void validateQuestionsExist(Collection<Long> qids) {
        if (qids == null || qids.isEmpty()) return;
        Set<Long> distinct = new LinkedHashSet<>(qids);
        List<BizQuestion> found = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            questionMapper.selectBatchIds(distinct)));
        Set<Long> foundIds = found == null ? Set.of()
            : found.stream().map(BizQuestion::getId).collect(Collectors.toSet());
        List<Long> missing = distinct.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new ServiceException("题引用不存在（biz_question 无此题）: " + missing, 400);
        }
    }

    /** qids 入参：一律按字符串解析（🔴 雪花号 JSON 数字字面量会截尾）。 */
    private List<Long> qidsOf(Object raw) {
        List<Long> out = new ArrayList<>();
        if (!(raw instanceof List<?> l)) return out;
        for (Object o : l) {
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) continue;
            try {
                out.add(Long.valueOf(s));
            } catch (NumberFormatException e) {
                throw new ServiceException("qid 非法：" + s, 400);
            }
        }
        return out;
    }

    private List<String> goalsOf(Map<String, Object> meta) {
        List<String> out = new ArrayList<>();
        if (meta.get("goals") instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) out.add(String.valueOf(o));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reviewOf(Map<String, Object> meta) {
        Object r = meta.get("review");
        Map<String, Object> out = new LinkedHashMap<>();
        if (r instanceof Map) out.putAll((Map<String, Object>) r);
        out.putIfAbsent("status", "pending");
        out.putIfAbsent("issues", new ArrayList<Map<String, Object>>());
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> issuesOf(Map<String, Object> review) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (review.get("issues") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map) out.add(new LinkedHashMap<>((Map<String, Object>) o));
            }
        }
        return out;
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> m = JsonUtils.parseMap(json);
            return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
        } catch (Exception e) {
            log.warn("[punch] JSON 解析失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        return o != null && ("true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o)));
    }

    private String str(Object o, String dft) {
        if (o == null) return dft;
        String s = String.valueOf(o);
        return s.isBlank() ? dft : s;
    }
}
