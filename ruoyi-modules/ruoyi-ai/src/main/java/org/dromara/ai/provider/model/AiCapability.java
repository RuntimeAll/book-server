package org.dromara.ai.provider.model;

/**
 * AI provider 能力声明 — provider 实现类通过 {@link org.dromara.ai.provider.IAiProvider#capabilities()}
 * 自报具备哪些能力，router 在 fail-over 选路时按能力过滤候选 provider。
 *
 * <p>PRD-B-006 期仅定枚举；PRD-B-007 期 provider 实现 capabilities() 时返回自己具备的子集。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public enum AiCapability {

    /** 纯文本生成（必备能力，所有 text provider 都有） */
    TEXT,

    /** 视觉理解（图片输入 + 文本输出）— B-007 视觉模型选路用 */
    VISION,

    /** JSON 模式（强制返回合法 JSON）— 拆题场景必备 */
    JSON_MODE,

    /** Tool use / function calling — Agent 编排场景用 */
    TOOL_USE,

    /** 流式输出（SSE）— B-007 暂不启用，留接口 */
    STREAM,

    /** Prompt caching（Anthropic / 部分中转）— 降本场景用 */
    PROMPT_CACHING

}
