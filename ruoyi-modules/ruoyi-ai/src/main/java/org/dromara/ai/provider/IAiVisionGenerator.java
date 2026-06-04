package org.dromara.ai.provider;

import org.dromara.ai.provider.model.AiRequest;
import org.dromara.ai.provider.model.AiResponse;

/**
 * AI 视觉生成契约 — 图片 + 文本输入，文本输出。
 *
 * <p>PRD-B-006 期：仅留接口；PRD-B-007 期暂不实现（业务尚未启用视觉）；
 * 远期 PRD（如手写答题 OCR 后接 LLM 校对）启用时填实现。</p>
 *
 * <p>实现侧扩展点：{@link AiRequest#getMessages()} 的 Message 需扩展为支持 multipart
 * （文本块 + 图片块混合），B-007 期视实际使用情况决定是否扩 Message 结构或新增
 * MultipartMessage 子类。本期不预先扩，避免无端复杂。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public interface IAiVisionGenerator extends IAiProvider {

    /**
     * 视觉生成 — 图片输入由调用方在 AiRequest 中以待扩展的方式传入（B-007/远期定义）。
     *
     * @param request 调用入参（视觉场景需含图片）
     * @return AI 响应
     */
    AiResponse generateVision(AiRequest request);

}
