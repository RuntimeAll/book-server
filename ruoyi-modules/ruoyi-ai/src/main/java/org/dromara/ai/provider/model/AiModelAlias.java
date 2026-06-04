package org.dromara.ai.provider.model;

/**
 * AI 模型别名 — 业务侧调用时只认别名，由 router 决定路由到哪个 provider 的哪个 model。
 *
 * <p>设计意图：业务代码不写死具体 model 名（如 claude-3-5-sonnet-20241022），
 * 改成"我要一个 fast / strong / light 的模型"，便于切换 provider / 升级模型版本。</p>
 *
 * <p>PRD-B-006 期：仅定义枚举值；PRD-B-007 期：AiProperties.aliases 映射到具体 provider+model。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public enum AiModelAlias {

    /**
     * Claude 系列 - 快速档（成本低 / 延迟低 / 准确度中）。
     * 默认指向 claude-3-5-haiku 类模型，适合分类 / 简单抽取 / 改写。
     */
    CLAUDE_FAST,

    /**
     * Claude 系列 - 强档（成本高 / 延迟中 / 准确度高）。
     * 默认指向 claude-3-5-sonnet 类模型，适合题目拆解 / 知识点对齐 / 复杂推理。
     */
    CLAUDE_STRONG,

    /**
     * Claude 系列 - 轻量档（成本最低 / 适合大批量 / 准确度可接受）。
     * 默认指向 claude-3-haiku 类模型，适合摘要 / 路由前置过滤。
     */
    CLAUDE_LIGHT

}
