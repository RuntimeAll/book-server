package org.dromara.book.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * toolkit 拆题端点 HTTP 客户端（PRD-A-002 路B）。
 *
 * <p>POST {@code {toolkit.base-url}/split}（无鉴权裸 JSON），用 JDK {@link HttpClient}（免新依赖）。
 * HttpClient 默认不走系统代理（无 ProxySelector），天然绕本地代理，OK。超时 ~120s（拆整卷 ~40s，留余量）。
 *
 * <p>请求：{@code {markdown?, image_base64?:str|[str], answer_mode, grade_hint?, min_chars?}}；
 * 响应：{@code {ok, questions:[{stem,qtype,options,has_figure,answer,analysis,dna?}], dropped, count, error}}。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Slf4j
@Component
public class SplitClient {

    /** 连接 + 整体超时 120s（拆整卷 ~40s，留 3x 余量） */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    // 🔴 必须锁 HTTP_1_1：JDK HttpClient 默认 HTTP_2，向 uvicorn(h11 仅 HTTP/1.1) 发 HTTP/2 preface
    // 会被拒为 "Invalid HTTP request received" → 400（实测踩坑）。toolkit 是 HTTP/1.1 服务。
    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /** 独立 ObjectMapper：未知字段忽略，与全局序列化解耦。 */
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${toolkit.base-url:http://localhost:8095}")
    private String baseUrl;

    /**
     * 调 toolkit /split 拆题。
     *
     * @param markdown    文字层 markdown（与 imageBase64 二选一，markdown 非空优先）
     * @param imageBase64 图 base64（单图传 1 元素 list；与 markdown 二选一）
     * @param answerMode  from_source / ai_solve / stem_only
     * @param gradeHint   学段提示，可空
     * @param minChars    完整度过滤最小字符数，可空（null 则不传）
     * @return 解析后的拆题结果
     * @throws Exception 网络/超时/HTTP 非 2xx/解析失败 —— 调用方（worker）捕获置 FAILED
     */
    public SplitResponse split(String markdown, List<String> imageBase64,
                               String answerMode, String gradeHint, Integer minChars) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (markdown != null && !markdown.isBlank()) {
            body.put("markdown", markdown);
        }
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            // 单图传 string，多图传数组（toolkit 两者皆收）
            body.put("image_base64", imageBase64.size() == 1 ? imageBase64.get(0) : imageBase64);
        }
        body.put("answer_mode", answerMode == null ? "from_source" : answerMode);
        if (gradeHint != null && !gradeHint.isBlank()) {
            body.put("grade_hint", gradeHint);
        }
        if (minChars != null) {
            body.put("min_chars", minChars);
        }
        String reqJson = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/split"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String bodySnippet = resp.body() == null ? "" : resp.body().substring(0, Math.min(500, resp.body().length()));
            throw new IllegalStateException("toolkit /split HTTP " + resp.statusCode() + "：" + bodySnippet);
        }
        return parse(resp.body());
    }

    /** 手工解析：questions 内 options/dna 等异构，用 JsonNode 容错读，避免严格映射崩。 */
    private SplitResponse parse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        SplitResponse out = new SplitResponse();
        out.setOk(root.path("ok").asBoolean(false));
        out.setCount(root.path("count").asInt(0));
        out.setError(root.hasNonNull("error") ? root.get("error").asText() : null);

        List<String> dropped = new ArrayList<>();
        JsonNode dropNode = root.path("dropped");
        if (dropNode.isArray()) {
            for (JsonNode d : dropNode) {
                dropped.add(d.asText());
            }
        }
        out.setDropped(dropped);

        List<SplitQuestion> questions = new ArrayList<>();
        JsonNode qNode = root.path("questions");
        if (qNode.isArray()) {
            for (JsonNode q : qNode) {
                SplitQuestion sq = new SplitQuestion();
                sq.setStem(textOrNull(q, "stem"));
                sq.setQtype(textOrNull(q, "qtype"));
                sq.setAnswer(textOrNull(q, "answer"));
                sq.setAnalysis(textOrNull(q, "analysis"));
                sq.setHasFigure(q.path("has_figure").asBoolean(false));
                List<String> options = new ArrayList<>();
                JsonNode optNode = q.path("options");
                if (optNode.isArray()) {
                    for (JsonNode o : optNode) {
                        options.add(o.asText());
                    }
                }
                sq.setOptions(options);
                // dna 整段原样留 JSON 字符串（含 difficulty 等），落库 dna_json
                JsonNode dnaNode = q.get("dna");
                if (dnaNode != null && !dnaNode.isNull() && dnaNode.isObject()) {
                    sq.setDnaJson(mapper.writeValueAsString(dnaNode));
                    if (dnaNode.has("difficulty") && dnaNode.get("difficulty").isNumber()) {
                        sq.setDnaDifficulty(dnaNode.get("difficulty").asInt());
                    }
                }
                questions.add(sq);
            }
        }
        out.setQuestions(questions);
        return out;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    // ==================== 响应模型 ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SplitResponse implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private boolean ok;
        private List<SplitQuestion> questions = new ArrayList<>();
        private List<String> dropped = new ArrayList<>();
        private int count;
        private String error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SplitQuestion implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 题干 Markdown+$LaTeX$ */
        private String stem;
        /** 题型文本 "选择"/"填空"/"解答" */
        private String qtype;
        /** 选项 */
        private List<String> options = new ArrayList<>();
        /** 含图 */
        private boolean hasFigure;
        /** 答案 */
        private String answer;
        /** 解析 */
        private String analysis;
        /** dna 整段 JSON（可空） */
        private String dnaJson;
        /** dna.difficulty（1-4，可空） */
        private Integer dnaDifficulty;
    }
}
