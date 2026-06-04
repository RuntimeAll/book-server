package org.dromara.ai.provider.model;

import lombok.Data;

/**
 * AI 响应格式约束 — 业务在 {@link AiRequest#getResponseFormat()} 中指定期望返回格式。
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>{@link Type#TEXT} — 默认，返回自然语言文本</li>
 *   <li>{@link Type#JSON_OBJECT} — 强制返回合法 JSON 对象（拆题 / 知识点抽取）</li>
 *   <li>{@link Type#JSON_SCHEMA} — 按 schema 字段定义的 JSON Schema 严格返回（更稳）</li>
 * </ul>
 *
 * <p>PRD-B-006 期：仅定义结构；PRD-B-007 期：provider 实现按 type 拼请求体（Anthropic / OpenAI 不同）。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class ResponseFormat {

    /**
     * 格式类型枚举
     */
    public enum Type {
        /** 自然语言文本（默认） */
        TEXT,
        /** 强制 JSON 对象 */
        JSON_OBJECT,
        /** 按 JSON Schema 严格返回 */
        JSON_SCHEMA
    }

    /** 格式类型 */
    private Type type = Type.TEXT;

    /**
     * JSON Schema 内容（仅 {@link Type#JSON_SCHEMA} 时使用）。
     * 应是符合 JSON Schema Draft 2020-12 的对象的 JSON 字符串。
     */
    private String schema;

}
