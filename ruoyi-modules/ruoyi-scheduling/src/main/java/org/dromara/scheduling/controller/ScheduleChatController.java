package org.dromara.scheduling.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主线 C · AI 多轮追问 + 槽位提取（骨架 Step 5）
 *
 * <p>POST /schedule/chat/extract 接 NL，调星图聚合 opus-4-7 + extract_lessons tool
 *
 * <p>凭据：从环境变量 STAR_AGGREGATOR_API_KEY 读（启动脚本 start-be.ps1 从 password/ 加载）
 *
 * <p>对应 spike S1.2 + S3 的 Java 端口
 */
@RestController
@RequestMapping("/schedule/chat")
@RequiredArgsConstructor
public class ScheduleChatController {

    private final ObjectMapper objectMapper;

    @Value("${schedule.star.api-key:}")
    private String apiKey;

    @Value("${schedule.star.base-url:https://api.lk888.ai}")
    private String baseUrl;

    @Value("${schedule.star.model-id:claude-opus-4-7}")
    private String modelId;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String EXTRACT_LESSONS_TOOL_JSON = """
            {
              "name": "extract_lessons",
              "description": "从老师的排课自然语言中提取一天内 ≥1 节课的槽位信息。每节课为一个对象。缺失字段记入 missing_fields，模糊表达记入 ambiguity，违反 MVP 范围记入 out_of_mvp_scope。",
              "input_schema": {
                "type": "object",
                "properties": {
                  "lessons": {
                    "type": "array",
                    "items": {
                      "type": "object",
                      "properties": {
                        "date":         { "type": "string",  "description": "ISO YYYY-MM-DD" },
                        "start_time":   { "type": "string",  "description": "HH:MM 24h" },
                        "duration_min": { "type": "integer", "description": "持续分钟数" },
                        "location":     { "type": "string",  "description": "地点名（原样保留）" },
                        "lesson_name":  { "type": "string",  "description": "课程名称（自由文本）" }
                      }
                    }
                  },
                  "missing_fields":   { "type": "array", "items": { "type": "string" }, "description": "需追问字段，如 lesson[0].duration_min" },
                  "ambiguity":        { "type": "array", "items": { "type": "string" }, "description": "模糊点描述" },
                  "out_of_mvp_scope": { "type": "array", "items": { "type": "string" }, "description": "MVP 不支持的场景（跨天 / 周期）" }
                },
                "required": ["lessons", "missing_fields", "ambiguity", "out_of_mvp_scope"]
              }
            }
            """;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            今天是 %s。
            你是 MVP 排课助手，负责从老师 NL 输入中用 extract_lessons 工具提取槽位。
            【MVP 规则】
            - 只支持单天排课，跨天 / 周期规则 ("每周一三") 必须记入 out_of_mvp_scope
            - 单天可多节
            - 缺失字段必须记入 missing_fields（用 lesson[i].field 形式定位）
            - 模糊表达必须记入 ambiguity（如"周末"未明示周六还是周日）
            - 闲聊噪音忽略，只提取排课信息
            """;

    @SaIgnore
    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestBody Map<String, String> body) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("error", "STAR_AGGREGATOR_API_KEY 未配置，请用 PRD-C/_spike/code/start-be.ps1 启动 BE 加载 password 环境变量");
        }
        String userMessage = body == null ? null : body.get("userMessage");
        if (userMessage == null || userMessage.isBlank()) {
            return Map.of("error", "userMessage 不能为空");
        }

        String today = LocalDate.now().toString();
        String system = String.format(SYSTEM_PROMPT_TEMPLATE, today);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", modelId);
        req.put("max_tokens", 1024);
        req.put("system", system);
        req.put("tools", List.of(objectMapper.readValue(EXTRACT_LESSONS_TOOL_JSON, Map.class)));
        req.put("tool_choice", Map.of("type", "tool", "name", "extract_lessons"));
        req.put("messages", List.of(Map.of("role", "user", "content", userMessage)));

        String reqJson = objectMapper.writeValueAsString(req);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
                .build();

        long t0 = System.currentTimeMillis();
        HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long dt = System.currentTimeMillis() - t0;

        if (resp.statusCode() != 200) {
            return Map.of(
                    "error", "Star aggregator HTTP " + resp.statusCode(),
                    "body", resp.body(),
                    "elapsed_ms", dt
            );
        }

        Map<String, Object> respObj = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) respObj.get("content");
        for (Map<String, Object> block : content) {
            if ("tool_use".equals(block.get("type"))) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("extracted", block.get("input"));
                out.put("usage", respObj.get("usage"));
                out.put("stop_reason", respObj.get("stop_reason"));
                out.put("elapsed_ms", dt);
                return out;
            }
        }
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", "no tool_use in response");
        err.put("raw", respObj);
        err.put("elapsed_ms", dt);
        return err;
    }
}
