package org.dromara.ai.observability;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 调用日志条目 — 1:1 对应未来 biz_ai_call_log 表的列。
 *
 * <p>PRD-B-006 期：仅 POJO 定型；PRD-B-007 期：DbAiCallLogger 落 MySQL 表（biz_ai_call_log 见 B-007 V{n} 迁移）。</p>
 *
 * <p>关键字段说明：</p>
 * <ul>
 *   <li>{@link #providerUsed} / {@link #modelUsed} — 真实调用结果，便于追踪 fail-over 路由</li>
 *   <li>{@link #tokensInput} / {@link #tokensOutput} / {@link #tokensCached} — 成本核算</li>
 *   <li>{@link #latencyMillis} — provider 端到端耗时</li>
 *   <li>{@link #success} + {@link #errorMessage} — 失败原因分类用</li>
 *   <li>{@link #metadata} — 业务上下文透传（question_id / paper_id / wf_step）</li>
 * </ul>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class AiCallLogEntry {

    /** 主键（B-007 期 ASSIGN_ID 雪花） */
    private Long id;

    /** 调用别名（如 CLAUDE_FAST） */
    private String alias;

    /** 实际使用的 provider 名 */
    private String providerUsed;

    /** 实际使用的 model 名 */
    private String modelUsed;

    /** 输入 token */
    private Integer tokensInput;

    /** 输出 token */
    private Integer tokensOutput;

    /** 缓存命中 token */
    private Integer tokensCached;

    /** 端到端耗时（毫秒） */
    private Long latencyMillis;

    /** 是否成功（true / false） */
    private Boolean success;

    /** 失败原因（success=false 时填）；成功时 null */
    private String errorMessage;

    /** 停止原因 — 透传 {@link org.dromara.ai.provider.model.AiResponse#getStopReason()} */
    private String stopReason;

    /** 调用时间（请求发出时刻） */
    private LocalDateTime callTime;

    /**
     * 业务元数据 — 透传 {@link org.dromara.ai.provider.model.AiRequest#getMetadata()}，
     * B-007 期可落 JSON 列方便排查（推荐 MySQL JSON 类型）。
     */
    private Map<String, Object> metadata;

    /** 创建人（sys_user.user_id，可空 — 系统调用为 null） */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

}
