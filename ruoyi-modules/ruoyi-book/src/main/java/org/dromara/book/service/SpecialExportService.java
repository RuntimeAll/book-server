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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** 主题目录（classpath），整目录含 sujunyu-v1.html + katex/ 字体，渲染前整份拷到临时目录。 */
    private static final String THEME_PREFIX = "export-themes/sujunyu-v1/";
    private static final String THEME_HTML = "sujunyu-v1.html";
    private static final String DATA_TOKEN = "__PAPER_DATA__";

    /** 探测的 Chrome/Edge 可执行文件（首个存在者胜）。 */
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

    private static final long CHROME_TIMEOUT_MS = 60_000L;

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

        Path work;
        try {
            work = Files.createTempDirectory("special-export-");
            copyTheme(work);
        } catch (Exception e) {
            log.error("[special-export] 准备主题失败", e);
            throw new ServiceException("导出环境准备失败: " + e.getMessage(), 500);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("specialId", String.valueOf(specialId));
        try {
            for (String paper : papers) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("title", title);
                data.put("date", date);
                data.put("paper", paper);
                data.put("withAnalysis", withAnalysis);
                data.put("withStars", withStars);
                data.put("defaultGap", 24);
                data.put("sections", sections);
                byte[] pdf = renderPdf(work, data, paper);
                OssClient oss = OssFactory.instance();
                UploadResult up = oss.uploadSuffix(pdf, ".pdf", "application/pdf");
                result.put("question".equals(paper) ? "questionUrl" : "answerUrl", up.getUrl());
                log.info("[special-export] {} 卷生成 size={}B url={}", paper, pdf.length, up.getUrl());
            }
        } finally {
            deleteQuietly(work);
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
            String fig = resolveFigure(ov, q);
            if (fig != null) qo.put("figure", fig);
            Object gap = it.get("gap");
            if (gap != null) qo.put("gap", intOf(gap));
            qo.put("answer", resolveAnswer(ov, q));
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
            if (notBlank(q.getStemTextContent())) return q.getStemTextContent();
            if (notBlank(q.getStemText())) return q.getStemText();
            String fromBlock = stemFromBlock(q.getBlockJson());
            if (fromBlock != null) return fromBlock;
        }
        return "（题干缺失）";
    }

    private String resolveAnswer(Map<String, Object> ov, QuestionDetailVo q) {
        String s = str(ov.get("answer"), null);
        if (s != null) return s;
        if (q != null) {
            if (notBlank(q.getAnswerTextContent())) return q.getAnswerTextContent();
            if (notBlank(q.getAnswer())) return q.getAnswer();
        }
        return "";
    }

    private String resolveAnalysis(Map<String, Object> ov, QuestionDetailVo q) {
        String s = str(ov.get("analysis"), null);
        if (s != null) return s;
        if (q != null) {
            if (notBlank(q.getAnalyzeTextContent())) return q.getAnalyzeTextContent();
            if (notBlank(q.getExplain())) return q.getExplain();
        }
        return null;
    }

    private String resolveFigure(Map<String, Object> ov, QuestionDetailVo q) {
        String s = str(ov.get("figure"), null);
        if (s != null) return s;
        return q != null && notBlank(q.getStemImg()) ? q.getStemImg() : null;
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
                        if (!md.isBlank()) sb.append(md);
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
            String md = c.path("md").asText("");
            if (!md.isBlank()) sb.append(md);
        }
        return sb.toString().trim();
    }

    // ───────────────── 渲染（无头 Chrome） ─────────────────

    private byte[] renderPdf(Path work, Map<String, Object> data, String paper) {
        try {
            String json = om.writeValueAsString(data);
            String tpl = Files.readString(work.resolve(THEME_HTML), StandardCharsets.UTF_8);
            // 单注入点替换（json 里的 $ 不能被当作正则替换组）
            String html = tpl.replace(DATA_TOKEN, json);
            Path htmlOut = work.resolve("paper-" + paper + ".html");
            Files.writeString(htmlOut, html, StandardCharsets.UTF_8);
            Path pdfOut = work.resolve("paper-" + paper + ".pdf");

            String chrome = resolveChrome();
            List<String> cmd = new ArrayList<>();
            cmd.add(chrome);
            cmd.add("--headless=new");
            cmd.add("--disable-gpu");
            cmd.add("--no-sandbox");
            cmd.add("--no-pdf-header-footer");
            cmd.add("--virtual-time-budget=8000");
            cmd.add("--run-all-compositor-stages-before-draw");
            cmd.add("--print-to-pdf=" + pdfOut.toAbsolutePath());
            cmd.add(htmlOut.toAbsolutePath().toUri().toString());

            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            // 读干输出防阻塞
            byte[] logbytes;
            try (InputStream in = p.getInputStream()) {
                logbytes = in.readAllBytes();
            }
            boolean done = p.waitFor(CHROME_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new ServiceException("PDF 渲染超时", 500);
            }
            if (!Files.exists(pdfOut) || Files.size(pdfOut) == 0) {
                log.error("[special-export] chrome 无产物 exit={} log={}", p.exitValue(),
                    new String(logbytes, StandardCharsets.UTF_8));
                throw new ServiceException("PDF 渲染失败（无产物）", 500);
            }
            return Files.readAllBytes(pdfOut);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[special-export] 渲染异常 paper={}", paper, e);
            throw new ServiceException("PDF 渲染异常: " + e.getMessage(), 500);
        }
    }

    private String resolveChrome() {
        String env = System.getenv("CHROME_BIN");
        if (notBlank(env) && Files.exists(Path.of(env))) return env;
        for (String c : CHROME_CANDIDATES) {
            if (c == null) continue;
            try {
                if (Files.exists(Path.of(c))) return c;
            } catch (Exception ignore) {
            }
        }
        throw new ServiceException("未找到 Chrome/Edge，设 CHROME_BIN 环境变量指定", 500);
    }

    /** 把 classpath:export-themes/sujunyu-v1/** 整目录拷进 work（保留相对路径）。 */
    private void copyTheme(Path work) throws Exception {
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
            throw new ServiceException("导出主题资源缺失（export-themes/sujunyu-v1）", 500);
        }
    }

    private void deleteQuietly(Path dir) {
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
