package org.dromara.admincommon.ocr.provider.model;

/**
 * OCR provider 能力声明 — provider 实现类通过 {@link org.dromara.ocr.provider.IOcrProvider#capabilities()}
 * 自报具备哪些能力，调用方按能力筛选。
 *
 * <p>PRD-B-006 期仅定枚举；PRD-B-007 期 TextinOcrProvider 实现时返回支持的子集。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public enum OcrCapability {

    /** PDF 识别（多页） */
    PDF,

    /** 图片识别（PNG / JPG / JPEG） */
    IMAGE,

    /** 公式识别（LaTeX 输出） */
    FORMULA,

    /** 手写识别 */
    HANDWRITING,

    /** 表格识别（结构化输出） */
    TABLE

}
