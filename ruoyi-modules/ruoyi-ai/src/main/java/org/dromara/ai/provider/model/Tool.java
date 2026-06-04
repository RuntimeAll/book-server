package org.dromara.ai.provider.model;

import lombok.Data;

/**
 * AI tool / function 定义 — 业务在 {@link AiRequest#getTools()} 列表中传入，
 * provider 实现按各家协议（Anthropic tools / OpenAI functions）转译。
 *
 * <p>PRD-B-006 期：仅定 schema；PRD-B-007 期：业务方组 Tool 调 LLM 时按本结构填字段。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class Tool {

    /**
     * 工具名 — 字母数字下划线，建议 snake_case，例如 "search_knowledge_base"。
     * 必填。
     */
    private String name;

    /**
     * 工具描述 — 给 LLM 看的自然语言说明，决定 LLM 何时选用该工具。
     * 必填。建议清晰说明"做什么"+"什么场景用"。
     */
    private String description;

    /**
     * 工具入参 JSON Schema（Draft 2020-12 兼容子集）字符串。
     * 必填。LLM 按此 schema 生成 {@link ToolCall#getInput()}。
     */
    private String inputSchema;

}
