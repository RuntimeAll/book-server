package org.dromara.ai.provider;

import org.dromara.ai.provider.model.AiRequest;
import org.dromara.ai.provider.model.AiResponse;

/**
 * AI 文本生成契约 — 业务侧最主要的入口。
 *
 * <p>典型用法（PRD-B-007 后）：</p>
 * <pre>
 *   {@code @Resource}
 *   private IAiTextGenerator aiText;
 *
 *   AiRequest req = new AiRequest();
 *   req.setAlias(AiModelAlias.CLAUDE_FAST);
 *   req.setMessages(List.of(...));
 *   AiResponse resp = aiText.generate(req);
 *   String content = resp.getContent();
 * </pre>
 *
 * <p>注入点说明：</p>
 * <ul>
 *   <li>若注入本接口直接拿到的是 router bean（由 AiProviderRouter 实现，B-007 填），
 *       业务侧不感知具体 provider</li>
 *   <li>具体 provider 实现也实现本接口，但 bean 通常 qualifier 区分 (@Qualifier("lk888"))</li>
 * </ul>
 *
 * <p>PRD-B-006 期：仅定义契约；PRD-B-007 期：Lk888AiProvider / AigeekAiProvider / AiProviderRouter 实现。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public interface IAiTextGenerator extends IAiProvider {

    /**
     * 生成文本响应 — 同步调用，等待 LLM 返完。
     *
     * <p>实现要求（B-007 落实）：</p>
     * <ul>
     *   <li>调用前后必触发 {@link org.dromara.ai.observability.IAiCallLogger} 落日志</li>
     *   <li>遇网络异常 / 5xx 抛 {@code AiProviderException} (B-007 定义)，由 router 决定是否 fail-over</li>
     *   <li>遇 4xx (鉴权失败 / 入参错) 直接抛业务异常，不重试</li>
     *   <li>超时按 {@link AiRequest#getTimeout()}，未设置则取 {@link org.dromara.ai.config.AiProperties} 默认</li>
     * </ul>
     *
     * @param request 调用入参（必填 messages；alias / model 至少一个）
     * @return AI 响应（content + tokens 必填）
     */
    AiResponse generate(AiRequest request);

}
