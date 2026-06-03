package org.dromara.ocr.provider.model;

import lombok.Data;

import java.util.List;

/**
 * OCR 调用统一出参 — provider 实现把各家原响应映射到本结构返给业务侧。
 *
 * <p>PRD-B-006 期：字段定型；PRD-B-007 期：TextinOcrProvider 解析 textin JSON 填充。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class OcrResult {

    /**
     * 全文 markdown — provider 拼接好的整文档 markdown（含表格 / 公式 LaTeX / 图片占位）。
     * 业务侧最常用字段（PRD-B-007 拆题用 ruoyi-ai 喂 LLM 时直接用 markdown）。
     */
    private String markdown;

    /**
     * 分页结果 — 多页 PDF 时填多条；图片 1 条。
     */
    private List<OcrPage> pages;

    /**
     * 拍平后的块列表 — 跨页所有 block 拼一起，方便业务直接遍历。
     * 与 pages 内 blocks 是同一组对象引用，二选一使用即可。
     */
    private List<OcrBlock> blocks;

    /**
     * 原始响应（JSON 字符串）— provider 原始返回，便于排查 / 二次解析。
     * 长度可能较大，落日志时按需截断。
     */
    private String rawResponse;

    /**
     * 成功识别的页数 — successCount ≤ totalPages，textin 等 provider 部分页失败时差额。
     */
    private Integer successCount;

    /** 文档总页数 */
    private Integer totalPages;

    /** 实际使用的 provider 名（如 "textin"） */
    private String providerUsed;

}
