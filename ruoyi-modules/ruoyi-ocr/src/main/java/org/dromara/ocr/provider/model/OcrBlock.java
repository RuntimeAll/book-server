package org.dromara.ocr.provider.model;

import lombok.Data;

/**
 * OCR 识别块 — 一页内的最小语义单元（文本块 / 表格 / 图片 / 公式）。
 *
 * <p>PRD-B-006 期：仅 POJO 定型；PRD-B-007 期：TextinOcrProvider 解析 textin 响应填本结构。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class OcrBlock {

    /**
     * 块类型枚举
     */
    public enum Type {
        /** 普通文本段 */
        TEXT,
        /** 表格（text 字段含 markdown 表格） */
        TABLE,
        /** 图片块 — text 可能为图片描述 / OCR 内嵌图字符 */
        IMAGE,
        /** 公式（text 含 LaTeX 源码） */
        FORMULA
    }

    /** 块类型 */
    private Type type;

    /**
     * 块文本内容 — 按类型语义不同（普通文本 / markdown 表格 / LaTeX 公式 / 图片描述）。
     */
    private String text;

    /**
     * 边界框（bbox）— 8 元数组，对应 4 个顶点 (x1,y1,x2,y2,x3,y3,x4,y4)，
     * 顺时针从左上开始。坐标系为页面像素。
     * 可空（部分 provider 不返）。
     */
    private double[] bbox;

    /**
     * 识别置信度（0.0~1.0），可空。
     */
    private Double score;

    /**
     * 所属页 ID（对应 {@link OcrPage#getPageId()}）— 多页 PDF 时方便 block 反查页。
     */
    private Integer pageId;

}
