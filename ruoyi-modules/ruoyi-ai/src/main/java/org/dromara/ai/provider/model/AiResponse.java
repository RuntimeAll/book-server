package org.dromara.ai.provider.model;

import lombok.Data;

import java.util.List;

/**
 * AI 调用统一出参 — provider 实现把各家原响应映射到本结构返给业务侧。
 *
 * <p>PRD-B-006 期：字段定型；PRD-B-007 期：provider 实现解析各家 JSON 填充。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class AiResponse {

    /**
     * 文本内容（纯文本 / JSON 字符串均放这里，业务方按 {@link AiRequest#getResponseFormat()} 自解析）。
     * tool use 场景文本可能为空，只有 {@link #toolCalls}。
     */
    private String content;

    /**
     * 实际使用的模型名 — 即便业务传的是别名，这里也写解析后真实 model（便于排查 / 计费）。
     */
    private String modelUsed;

    /**
     * 实际使用的 provider 名（如 "lk888" / "aigeekapi" / "anthropic-direct"）— router 选路结果。
     */
    private String providerUsed;

    /**
     * Token 用量 — 成本核算关键字段，必填。
     */
    private TokenUsage tokens;

    /**
     * 停止原因 — "end_turn" / "max_tokens" / "tool_use" / "stop_sequence" 等；
     * 各家 provider 命名略不同，由 provider 实现层规范化。
     */
    private String stopReason;

    /**
     * Tool 调用列表 — LLM 决定调用工具时填；业务侧据此分发执行。
     * 无 tool use 场景为 null / 空。
     */
    private List<ToolCall> toolCalls;

}
