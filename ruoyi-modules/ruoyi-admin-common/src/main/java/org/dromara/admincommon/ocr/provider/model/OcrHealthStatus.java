package org.dromara.admincommon.ocr.provider.model;

/**
 * OCR provider 健康状态。
 *
 * <p>简化版（相比 AI 两态 UP/DOWN，无 DEGRADED）— OCR 场景没 fail-over 路由 v1（PRD §B），
 * 仅探测可用性。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public enum OcrHealthStatus {

    /** 健康可用 */
    UP,

    /** 不可用 */
    DOWN

}
