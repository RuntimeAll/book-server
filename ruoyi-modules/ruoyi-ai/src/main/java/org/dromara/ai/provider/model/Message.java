package org.dromara.ai.provider.model;

import lombok.Data;

/**
 * AI 对话消息单元 — 业务侧组多轮对话时把每轮装成 Message 加到 {@link AiRequest#getMessages()}。
 *
 * <p>角色定义参考 OpenAI / Anthropic 通行做法：</p>
 * <ul>
 *   <li>{@link Role#SYSTEM} — 系统指令（人设 / 任务约束），Anthropic 习惯放 request.system 而非 messages 数组里，
 *       provider 实现层适配（B-007 处理）</li>
 *   <li>{@link Role#USER} — 用户输入</li>
 *   <li>{@link Role#ASSISTANT} — 之前轮的 LLM 回复（多轮对话回传上下文）</li>
 *   <li>{@link Role#TOOL} — 工具执行结果（与 {@link ToolCall} 配对）</li>
 * </ul>
 *
 * <p>PRD-B-006 期：仅定 schema；PRD-B-007 期：provider 实现按各家协议转译。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class Message {

    /**
     * 消息角色
     */
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    /** 角色 */
    private Role role;

    /**
     * 消息内容（文本）。
     * 视觉场景 ({@link org.dromara.ai.provider.IAiVisionGenerator}) 后续可扩成 multipart，本期先纯文本。
     */
    private String content;

    /**
     * Prompt cache 控制标记 — 仅 {@link AiCapability#PROMPT_CACHING} provider 支持。
     * 取值典型 "ephemeral"（Anthropic 短期缓存）；null 表示不缓存。
     */
    private String cacheControl;

}
