package org.dromara.ai.provider.model;

/**
 * AI provider 健康状态 — router 根据熔断器 + 探测器输出决定路由。
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public enum AiHealthStatus {

    /** 健康可用 */
    UP,

    /** 部分降级（延迟高 / 错误率上升但仍可调用），router 优先级降低但仍会派单 */
    DEGRADED,

    /** 不可用（熔断 / 凭据失效 / 网络断），router 跳过 */
    DOWN

}
