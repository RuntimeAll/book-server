package org.dromara.book.service.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionSnapshotCodec {
    private final ObjectMapper objectMapper;

    public String encode(QuestionDetailVo question) {
        try {
            return objectMapper.writeValueAsString(question);
        } catch (JsonProcessingException e) {
            throw new ServiceException("题目快照序列化失败");
        }
    }

    public QuestionDetailVo decode(String json) {
        try {
            QuestionDetailVo question = objectMapper.readValue(json, QuestionDetailVo.class);
            if (question == null || question.getId() == null) {
                throw new ServiceException("题目快照无效");
            }
            return question;
        } catch (JsonProcessingException e) {
            throw new ServiceException("题目快照无效");
        }
    }

    public QuestionDetailVo resolve(QuestionDetailVo original, String overrideJson) {
        QuestionDetailVo question = decode(encode(original));
        if (overrideJson == null || overrideJson.isBlank()) {
            return question;
        }
        JsonNode overrides = readTree(overrideJson);
        if (!overrides.isObject()) {
            throw new ServiceException("书内题目覆盖数据无效", 400);
        }
        boolean stemChanged = overrides.has("stem");
        boolean optionsChanged = overrides.has("options");
        if (stemChanged || optionsChanged || overrides.has("blockJson") || overrides.has("figure")) {
            question.setAnswer(null);
            question.setAnswerTextContent(null);
            question.setAnswerImg(null);
            question.setAnswerBlockJson(null);
            question.setExplain(null);
            question.setAnalyzeTextContent(null);
            question.setExplainImg(null);
            question.setAnalyzeBlockJson(null);
        }
        if (stemChanged || optionsChanged) {
            ArrayNode rows = objectMapper.createArrayNode();
            JsonNode source = question.getBlockJson() == null ? null : readTree(question.getBlockJson());
            if (stemChanged) {
                String stem = nullableText(overrides.get("stem"));
                question.setStemText(stem);
                question.setStemTextContent(stem);
                question.setStemImg(null);
                if (stem != null && !stem.isEmpty()) {
                    addText(rows, stem);
                }
            } else if (source == null || !source.path("rows").isArray()) {
                String stem = question.getStemTextContent() != null
                    ? question.getStemTextContent() : question.getStemText();
                if (stem != null) {
                    addText(rows, stem);
                }
                if (question.getStemImg() != null) {
                    ObjectNode cell = objectMapper.createObjectNode().put("type", "image")
                        .put("url", question.getStemImg()).put("width", 100).put("align", "left");
                    rows.addObject().putArray("cells").add(cell);
                }
            }
            // Retain independent option cells when replacing only the stem, and vice versa.
            if (source != null && source.path("rows").isArray()) {
                for (JsonNode row : source.path("rows")) {
                    ArrayNode cells = objectMapper.createArrayNode();
                    for (JsonNode cell : row.path("cells")) {
                        boolean option = "option".equals(cell.path("type").asText());
                        if ((option && !optionsChanged) || (!option && !stemChanged)) {
                            cells.add(cell.deepCopy());
                        }
                    }
                    if (!cells.isEmpty()) {
                        rows.addObject().set("cells", cells);
                    }
                }
            }
            if (optionsChanged) {
                JsonNode options = overrides.get("options");
                if (!options.isNull() && !options.isArray()) {
                    throw new ServiceException("书内选项必须为数组或null", 400);
                }
                for (int i = 0; options.isArray() && i < options.size(); i++) {
                    ObjectNode cell = objectMapper.createObjectNode().put("type", "option")
                        .put("label", String.valueOf((char) ('A' + i)));
                    cell.putArray("content").addObject().put("type", "text")
                        .put("md", nullableText(options.get(i)));
                    rows.addObject().putArray("cells").add(cell);
                }
            }
            question.setBlockJson(rows.isEmpty() ? null : objectMapper.createObjectNode()
                .put("v", 1).set("rows", rows).toString());
        }
        if (overrides.has("answer")) {
            String answer = nullableText(overrides.get("answer"));
            question.setAnswer(answer);
            question.setAnswerTextContent(answer);
            question.setAnswerImg(null);
            question.setAnswerBlockJson(null);
        }
        String analyzeKey = overrides.has("analysis") ? "analysis" : overrides.has("explain") ? "explain" : "analyze";
        if (overrides.has(analyzeKey)) {
            String analyze = nullableText(overrides.get(analyzeKey));
            question.setExplain(analyze);
            question.setAnalyzeTextContent(analyze);
            question.setExplainImg(null);
            question.setAnalyzeBlockJson(null);
        }
        if (overrides.has("blockJson")) {
            question.setBlockJson(nullableJson(overrides.get("blockJson")));
        }
        if (overrides.has("answerBlockJson")) {
            question.setAnswerBlockJson(nullableJson(overrides.get("answerBlockJson")));
        }
        if (overrides.has("analyzeBlockJson")) {
            question.setAnalyzeBlockJson(nullableJson(overrides.get("analyzeBlockJson")));
        }
        if (overrides.has("figure")) {
            String figure = nullableText(overrides.get("figure"));
            question.setStemImg(figure);
            if (question.getBlockJson() != null) {
                JsonNode document = readTree(question.getBlockJson());
                if (!document.isObject() || !document.path("rows").isArray()) {
                    throw new ServiceException("题目结构化内容无效", 400);
                }
                ArrayNode rows = objectMapper.createArrayNode();
                for (JsonNode row : document.path("rows")) {
                    ArrayNode cells = objectMapper.createArrayNode();
                    for (JsonNode cell : row.path("cells")) {
                        if (!"image".equals(cell.path("type").asText())) {
                            cells.add(cell.deepCopy());
                        }
                    }
                    if (!cells.isEmpty()) {
                        rows.addObject().set("cells", cells);
                    }
                }
                if (figure != null && !figure.isBlank()) {
                    rows.addObject().putArray("cells").addObject().put("type", "image").put("url", figure)
                        .put("width", 100).put("align", "left");
                }
                question.setBlockJson(rows.isEmpty() ? null : objectMapper.createObjectNode()
                    .put("v", 1).set("rows", rows).toString());
            }
        }
        return question;
    }

    private void addText(ArrayNode rows, String text) {
        rows.addObject().putArray("cells").addObject().put("type", "text").put("md", text);
    }

    private JsonNode readTree(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null) {
                throw new ServiceException("题目结构化内容无效", 400);
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new ServiceException("题目结构化内容无效", 400);
        }
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new ServiceException("题目覆盖文本类型无效", 400);
        }
        return node.textValue();
    }

    private String nullableJson(JsonNode node) {
        return node.isNull() ? null : node.isTextual() ? node.textValue() : node.toString();
    }
}
