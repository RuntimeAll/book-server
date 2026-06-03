package org.dromara.ocr.provider;

import org.dromara.ocr.provider.model.OcrCapability;
import org.dromara.ocr.provider.model.OcrHealthStatus;
import org.dromara.ocr.provider.model.OcrRequest;
import org.dromara.ocr.provider.model.OcrResult;

import java.util.Set;

/**
 * OCR provider 顶层契约 — 所有 OCR provider 实现（TextinOcrProvider 等）必实现。
 *
 * <p>典型用法（PRD-B-007 后）：</p>
 * <pre>
 *   {@code @Resource}
 *   private IOcrProvider ocr;
 *
 *   OcrRequest req = new OcrRequest();
 *   req.setFileBytes(pdfBytes);
 *   req.setFileType(OcrRequest.FileType.PDF);
 *   OcrResult result = ocr.recognize(req);
 *   String markdown = result.getMarkdown();
 * </pre>
 *
 * <p>PRD-B-006 期：仅定义契约；PRD-B-007 期：TextinOcrProvider 填实现。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public interface IOcrProvider {

    /**
     * Provider 唯一标识 — 小写字母数字横线，例如 "textin"。
     * 调用方按 name 在 {@link org.dromara.ocr.config.OcrProperties} 中找配置。
     *
     * @return provider 名（非空）
     */
    String name();

    /**
     * Provider 能力声明 — 业务侧按能力选 provider（PDF / 图片 / 公式 / 手写 / 表格）。
     *
     * @return 本 provider 支持的能力集合
     */
    Set<OcrCapability> capabilities();

    /**
     * 健康状态查询 — 业务侧调用前可先检（fast fail）。
     *
     * @return 当前健康状态
     */
    OcrHealthStatus health();

    /**
     * OCR 识别 — 同步调用，等待 provider 处理完返完整结果。
     *
     * <p>实现要求（B-007 落实）：</p>
     * <ul>
     *   <li>遇网络异常 / 5xx 抛 {@code OcrProviderException} (B-007 定义)</li>
     *   <li>遇 4xx (鉴权失败 / 入参错) 直接抛业务异常，不重试</li>
     *   <li>大文件按 fileType 走对应接口（PDF / image）</li>
     *   <li>超时按 {@link OcrRequest#getTimeout()}，未设置则取 OcrProperties 默认</li>
     * </ul>
     *
     * @param request OCR 入参（fileBytes / fileType 必填）
     * @return OCR 结果（markdown 必填，pages / blocks 必至少一个非空）
     */
    OcrResult recognize(OcrRequest request);

}
