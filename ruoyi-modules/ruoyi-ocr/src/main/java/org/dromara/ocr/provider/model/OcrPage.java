package org.dromara.ocr.provider.model;

import lombok.Data;

import java.util.List;

/**
 * OCR 单页结果 — 多页 PDF 时每页一条。
 *
 * <p>PRD-B-006 期：仅 POJO 定型；PRD-B-007 期：TextinOcrProvider 按 textin 分页响应填充。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class OcrPage {

    /** 页码（从 1 开始）— textin 等 provider 默认 1-based */
    private Integer pageId;

    /** 页面宽度（像素） */
    private Integer width;

    /** 页面高度（像素） */
    private Integer height;

    /**
     * 本页的所有识别块 — 按视觉顺序（上→下，左→右）排列。
     */
    private List<OcrBlock> blocks;

}
