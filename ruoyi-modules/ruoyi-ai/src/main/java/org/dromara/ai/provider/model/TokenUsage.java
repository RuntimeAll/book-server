package org.dromara.ai.provider.model;

import lombok.Data;

/**
 * Token 计数 — 落入 {@link AiResponse} 用于成本核算 / 限流 / 观测。
 *
 * <p>PRD-B-007 期 DbAiCallLogger 把这三个字段写入 biz_ai_call_log 表对应列。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class TokenUsage {

    /** 输入 token 数（含 system / messages 全部 prompt） */
    private Integer input;

    /** 输出 token 数（completion） */
    private Integer output;

    /**
     * 命中缓存的 token 数（prompt caching 启用时），未启用或未命中为 0。
     * 业务关注：缓存命中越多越省钱。
     */
    private Integer cached;

}
