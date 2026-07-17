package org.dromara.book.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.entity.SpecialItem;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.SpecialItemMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

/**
 * 专项双卷 PDF 导出服务（PRD-003 P5 / D3，跨线契约 §4 C 位独占面）。
 *
 * <p>链路：{@link SpecialService#detail} 拿全树（含归属校验）→ 逐题经 {@link IQuestionService#selectById}
 * 解析出题干/选项/答案/解析/配图 → 映射成 {@code export-themes/sujunyu-v1} 主题的 {@code __PAPER_DATA__}
 * JSON → 无头 Chrome {@code --print-to-pdf} 渲染出题目卷 + 答案卷 → OSS 上传 → 导出即对涉及 item
 * {@code used_count+1}（biz_shelf_item）。
 *
 * <p>🔴 卷面纪律：区块/难度档名由老师侧保证干净知识点名（无内部词）；星标（★）仅 {@code withStars=true}
 * 时显示，默认隐藏——学生卷面不出现任何内部标记。答案卷【解析】仅 {@code withAnalysis=true} 附带。
 *
 * @author codeplace-C PRD-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialExportService {

    private final SpecialService specialService;
    private final IQuestionService questionService;
    private final SpecialItemMapper itemMapper;
    private final ObjectMapper om;
    private final ChromePdfRenderer renderer;

    /** 主题目录名（classpath export-themes/ 下），渲染细节在 {@link ChromePdfRenderer}。 */
    private static final String THEME = "sujunyu-v1";

    // ───────────────── 导出主流程 ─────────────────

    /**
     * 导出专项双卷 PDF。
     *
     * @param specialId 专项 id（归属校验在 detail 内）
     * @param body      {papers:['question'|'answer'], withAnalysis, withStars}
     * @return {specialId, questionUrl?, answerUrl?, markedCount}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> export(Long specialId, Map<String, Object> body) {
        Map<String, Object> detail = specialService.detail(specialId);   // 含归属校验

        List<String> papers = new ArrayList<>();
        Object rawPapers = body.get("papers");
        if (rawPapers instanceof List<?> l) {
            for (Object o : l) {
                String s = String.valueOf(o);
                if ("question".equals(s) || "answer".equals(s)) papers.add(s);
            }
        }
        if (papers.isEmpty()) {                                          // 缺省=双卷
            papers.add("question");
            papers.add("answer");
        }
        boolean withAnalysis = truthy(body.get("withAnalysis"));
        boolean withStars = truthy(body.get("withStars"));

        String title = str(detail.get("title"), "专项练习");
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"));

        // 组装公共 sections + 收集涉及的 item id（used_count+1）
        List<Long> touchedItemIds = new ArrayList<>();
        List<Map<String, Object>> sections = buildSections(
            (List<Map<String, Object>>) detail.getOrDefault("secs", List.of()), touchedItemIds);

        if (sections.isEmpty()) {
            throw new ServiceException("专项为空，请先添加区块与题目再导出", 400);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("specialId", String.valueOf(specialId));
        for (String paper : papers) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("title", title);
            data.put("date", date);
            data.put("paper", paper);
            data.put("withAnalysis", withAnalysis);
            data.put("withStars", withStars);
            data.put("defaultGap", 24);
            data.put("sections", sections);
            byte[] pdf = renderer.render(THEME, data, paper);
            OssClient oss = OssFactory.instance();
            UploadResult up = oss.uploadSuffix(pdf, ".pdf", "application/pdf");
            result.put("question".equals(paper) ? "questionUrl" : "answerUrl", up.getUrl());
            log.info("[special-export] {} 卷生成 size={}B url={}", paper, pdf.length, up.getUrl());
        }

        // 导出即计数：used_count+1（无题则跳过）
        int marked = 0;
        if (!touchedItemIds.isEmpty()) {
            marked = itemMapper.update(null, new LambdaUpdateWrapper<SpecialItem>()
                .in(SpecialItem::getId, touchedItemIds)
                .setSql("used_count = IFNULL(used_count,0) + 1"));
        }
        result.put("markedCount", marked);
        return result;
    }

    // ───────────────── sections 组装 ─────────────────

    private List<Map<String, Object>> buildSections(List<Map<String, Object>> secs, List<Long> touched) {
        List<Map<String, Object>> out = new ArrayList<>();
        int[] no = {0};
        for (Map<String, Object> sec : secs) {
            Map<String, Object> secOut = new LinkedHashMap<>();
            secOut.put("name", str(sec.get("name"), ""));
            // 直挂 sec 的题
            secOut.put("questions", mapQuestions(listOf(sec.get("items")), no, touched));
            // tiers
            List<Map<String, Object>> tiersOut = new ArrayList<>();
            for (Map<String, Object> tier : listOf(sec.get("tiers"))) {
                Map<String, Object> tierOut = new LinkedHashMap<>();
                Map<String, Object> meta = asMap(tier.get("meta"));
                tierOut.put("label", str(tier.get("name"), ""));
                if (meta.get("star") != null) tierOut.put("star", intOf(meta.get("star")));
                if (meta.get("gap") != null) tierOut.put("gap", intOf(meta.get("gap")));
                tierOut.put("questions", mapQuestions(listOf(tier.get("items")), no, touched));
                tiersOut.add(tierOut);
            }
            secOut.put("tiers", tiersOut);
            // 跳过完全空区块（无题无档）
            boolean hasContent = !((List<?>) secOut.get("questions")).isEmpty()
                || tiersOut.stream().anyMatch(t -> !((List<?>) t.get("questions")).isEmpty());
            if (hasContent) out.add(secOut);
        }
        return out;
    }

    /** 把一节点内的 item 列表映射成主题 question 对象数组；累加题号 + 收集 item id。 */
    private List<Map<String, Object>> mapQuestions(List<Map<String, Object>> items, int[] no, List<Long> touched) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> it : items) {
            String kind = str(it.get("kind"), "question");
            if (!"question".equals(kind)) continue;
            Long itemId = longOf(it.get("id"));
            if (itemId != null) touched.add(itemId);
            Map<String, Object> ov = asMap(it.get("override"));
            Long qid = longOf(it.get("questionId"));
            QuestionDetailVo q = qid == null ? null : safeSelect(qid);

            Map<String, Object> qo = new LinkedHashMap<>();
            qo.put("no", ++no[0]);
            qo.put("stem", resolveStem(ov, q));
            List<String> opts = resolveOptions(ov, q);
            if (!opts.isEmpty()) qo.put("options", opts);
            List<String> figs = resolveFigures(ov, q);
            if (!figs.isEmpty()) qo.put("figures", figs);
            Object gap = it.get("gap");
            if (gap != null) qo.put("gap", intOf(gap));
            String answer = resolveAnswer(ov, q);
            if (notBlank(answer)) qo.put("answer", answer);
            String analysis = resolveAnalysis(ov, q);
            if (analysis != null) qo.put("analysis", analysis);
            out.add(qo);
        }
        return out;
    }

    private QuestionDetailVo safeSelect(Long qid) {
        try {
            return questionService.selectById(qid);
        } catch (Exception e) {
            log.warn("[special-export] 取题失败 qid={} err={}", qid, e.getMessage());
            return null;
        }
    }

    // ───────────────── 题面字段解析（override 优先，回落题库） ─────────────────

    private String resolveStem(Map<String, Object> ov, QuestionDetailVo q) {
        String s = str(ov.get("stem"), null);
        if (s != null) return s;
        if (q != null) {
            // blockJson text cells 优先：填空下划线（________）与内联 ![](url) 图只存在于 blockJson，
            // stem_text 里填空位是全角空格、图被剥离——优先 stem_text 会印出"没有下划线的空白"。
            String fromBlock = stemFromBlock(q.getBlockJson());
            if (fromBlock != null) return cleanStem(fromBlock);
            if (notBlank(q.getStemTextContent())) return cleanStem(q.getStemTextContent());
            if (notBlank(q.getStemText())) return cleanStem(q.getStemText());
        }
        return "（题干缺失）";
    }

    /**
     * 卷面题干清理：专项自带连续题号，题库题干残留的原书题号（"25．"）与
     * 图片选项题的裸标签行（"A．\tB．\tC．"，选项内容是图、文本只剩标签）都不该再上卷。
     */
    private String cleanStem(String stem) {
        if (!notBlank(stem)) return stem;
        String s = stem.replaceFirst("^\\s*\\d{1,3}[．.]\\s*", "");
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\r?\n")) {
            if (line.matches("\\s*(?:[A-D][．.]\\s*)+")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private String resolveAnswer(Map<String, Object> ov, QuestionDetailVo q) {
        String s = str(ov.get("answer"), null);
        if (s != null) return s;
        // 🔴 改编题干（override.stem 存在）但未改答案时不回落源题旧答案：新题面配旧答案会自相矛盾
        //    （答案卷印出与改编题干不符的原答案）。与 resolveOptions 同守卫。
        if (ov.get("stem") != null) return "";
        if (q != null) {
            if (notBlank(q.getAnswerTextContent())) return q.getAnswerTextContent();
            if (notBlank(q.getAnswer())) return q.getAnswer();
        }
        return "";
    }

    private String resolveAnalysis(Map<String, Object> ov, QuestionDetailVo q) {
        String s = str(ov.get("analysis"), null);
        if (s != null) return s;
        // 🔴 改编题干但未改解析时不回落源题旧解析（同 resolveAnswer/resolveOptions 守卫）。
        if (ov.get("stem") != null) return null;
        if (q != null) {
            if (notBlank(q.getAnalyzeTextContent())) return q.getAnalyzeTextContent();
            if (notBlank(q.getExplain())) return q.getExplain();
        }
        return null;
    }

    /**
     * 收集题干配图（多图）：override.figure > stem_img > blockJson 非选项行的 image cells
     * 与 text cells 内联 ![](url)。小学题库批次的图全在 blockJson image cells（stem_img=null），
     * 旧实现只看 stem_img 导致整卷图丢——此处统一收齐，顺序保持 blockJson 行序。
     */
    private List<String> resolveFigures(Map<String, Object> ov, QuestionDetailVo q) {
        List<String> out = new ArrayList<>();
        String s = str(ov.get("figure"), null);
        if (s != null) {
            out.add(s);
            return out;
        }
        if (q == null) return out;
        if (notBlank(q.getStemImg())) out.add(q.getStemImg());
        if (!notBlank(q.getBlockJson())) return out;
        try {
            JsonNode root = om.readTree(q.getBlockJson());
            for (JsonNode row : root.path("rows")) {
                boolean rowHasOption = false;
                for (JsonNode cell : row.path("cells")) {
                    if ("option".equals(cell.path("type").asText())) { rowHasOption = true; break; }
                }
                if (rowHasOption) continue;   // 选项图走 resolveOptions/mdOf，不重复收
                for (JsonNode cell : row.path("cells")) {
                    // 只收独立 image cells；text md 里的内联 ![](url) 由题干（blockJson 优先）
                    // 经模板 inlineMd 原位渲染，这里再收会在题末重复出图。
                    if ("image".equals(cell.path("type").asText())) {
                        String url = cell.path("url").asText("");
                        if (!url.isBlank() && !out.contains(url)) out.add(url);
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return out;
    }

    /** 从 blockJson 抽选项：cells type='option' → "A．内容"。 */
    private List<String> resolveOptions(Map<String, Object> ov, QuestionDetailVo q) {
        // 显式覆盖选项（含空列表=清空）时一律以 override 为准，不回落源题选项
        if (ov.containsKey("options")) {
            Object ovOpts = ov.get("options");
            List<String> out = new ArrayList<>();
            if (ovOpts instanceof List<?> l) {
                for (Object o : l) if (o != null) out.add(String.valueOf(o));
            }
            return out;
        }
        // 🔴 改编题干（override.stem 存在）时不回落源题旧选项：改编后的题干配原选择题选项会自相矛盾
        //    （题干已换成填空/范围题，下面却仍印 A/B/C/D）。修「override 源选项残留卷面」缺陷。
        if (ov.get("stem") != null) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        if (q == null || !notBlank(q.getBlockJson())) return out;
        try {
            JsonNode root = om.readTree(q.getBlockJson());
            for (JsonNode row : root.path("rows")) {
                for (JsonNode cell : row.path("cells")) {
                    if ("option".equals(cell.path("type").asText())) {
                        String label = cell.path("label").asText("");
                        String content = mdOf(cell.path("content"));
                        out.add((label.isBlank() ? "" : label + "．") + content);
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return out;
    }

    /** blockJson 无外置题干文本时，从 text cells 拼题干（option 行前的文本）。 */
    private String stemFromBlock(String blockJson) {
        if (!notBlank(blockJson)) return null;
        try {
            JsonNode root = om.readTree(blockJson);
            StringBuilder sb = new StringBuilder();
            for (JsonNode row : root.path("rows")) {
                boolean rowHasOption = false;
                for (JsonNode cell : row.path("cells")) {
                    if ("option".equals(cell.path("type").asText())) { rowHasOption = true; break; }
                }
                if (rowHasOption) break;
                for (JsonNode cell : row.path("cells")) {
                    if ("text".equals(cell.path("type").asText())) {
                        String md = cell.path("md").asText("");
                        if (!md.isBlank()) {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(md);
                        }
                    }
                }
            }
            String s = sb.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    private String mdOf(JsonNode content) {
        if (content == null || !content.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : content) {
            // 图片选项（选项内容是一张图）转 ![](url)，由模板 inlineMd 渲成 <img>——
            // 旧实现只拼 md 文本，图片选项被拼成空串（"A．"裸标签上卷）。
            if ("image".equals(c.path("type").asText())) {
                String url = c.path("url").asText("");
                if (!url.isBlank()) sb.append("![](").append(url).append(")");
                continue;
            }
            String md = c.path("md").asText("");
            if (!md.isBlank()) sb.append(md);
        }
        return sb.toString().trim();
    }

    // ───────────────── 小工具 ─────────────────

    private boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        return o != null && ("true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o)));
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String str(Object o, String dft) {
        if (o == null) return dft;
        String s = String.valueOf(o);
        return s.isEmpty() ? dft : s;
    }

    private Integer intOf(Object o) {
        if (o == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(o).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOf(Object o) {
        if (o == null) return null;
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Object o) {
        if (o instanceof List<?> l) return (List<Map<String, Object>>) l;
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return new LinkedHashMap<>();
    }
}
