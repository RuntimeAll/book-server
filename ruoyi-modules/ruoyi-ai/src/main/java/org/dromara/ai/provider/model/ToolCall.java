package org.dromara.ai.provider.model;

import lombok.Data;

/**
 * AI 工具调用结果 — LLM 返回 {@link AiResponse#getToolCalls()} 列表，业务侧据此执行真实动作（DB 查询 / 外部 API 等），
 * 然后再把执行结果以 role=tool 的 {@link Message} 回传给 LLM 进入下一轮。
 *
 * <p>PRD-B-006 期：仅定 schema；PRD-B-007 期：provider 实现解析各家响应填充。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class ToolCall {

    /**
     * 工具调用 ID — provider 生成的唯一标识，业务回传 tool 结果时按此 ID 关联。
     * 例如 Anthropic 是 "toolu_xxx"，OpenAI 是 "call_xxx"。
     */
    private String id;

    /**
     * 被调用的工具名（对应 {@link Tool#getName()}）
     */
    private String name;

    /**
     * 工具入参（LLM 按 {@link Tool#getInputSchema()} 生成的 JSON 字符串）。
     * 业务侧反序列化后执行。
     */
    private String input;

}
