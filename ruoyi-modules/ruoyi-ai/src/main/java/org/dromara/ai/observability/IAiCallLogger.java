package org.dromara.ai.observability;

import org.dromara.ai.provider.model.AiRequest;
import org.dromara.ai.provider.model.AiResponse;

/**
 * AI 调用日志契约 — provider 实现在每次调用前后必触发本接口落日志。
 *
 * <p>PRD-B-006 期：仅定义契约；PRD-B-007 期：DbAiCallLogger 实现把日志写 MySQL（biz_ai_call_log 表）；
 * 异步落（B-007 决定线程池 / disruptor）。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li>异步落日志，不阻塞业务主链路</li>
 *   <li>日志失败不抛业务异常，仅打 error log（业务正常返）</li>
 * </ul>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public interface IAiCallLogger {

    /**
     * 记录成功调用。
     *
     * @param request       原请求
     * @param response      LLM 响应
     * @param providerUsed  实际派单的 provider 名
     * @param latencyMillis 端到端耗时
     */
    void logSuccess(AiRequest request, AiResponse response, String providerUsed, long latencyMillis);

    /**
     * 记录失败调用。
     *
     * @param request       原请求
     * @param providerUsed  实际派单（或最后尝试）的 provider 名
     * @param latencyMillis 端到端耗时（到失败为止）
     * @param errorMessage  失败原因（异常 message / 错误码描述）
     */
    void logFailure(AiRequest request, String providerUsed, long latencyMillis, String errorMessage);

}
