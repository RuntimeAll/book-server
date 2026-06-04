package org.dromara.admincommon.ocr.provider.model;

import lombok.Data;

import java.time.Duration;
import java.util.Map;

/**
 * OCR 调用统一入参 — 业务侧无关 provider，构造此对象交给 {@link org.dromara.ocr.provider.IOcrProvider}。
 *
 * <p>PRD-B-006 期：字段定型；PRD-B-007 期：业务方按本结构组装请求调 TextinOcrProvider。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class OcrRequest {

    /**
     * 文件输入类型
     */
    public enum FileType {
        /** PDF 多页 */
        PDF,
        /** PNG 图片 */
        PNG,
        /** JPG / JPEG 图片 */
        JPG
    }

    /**
     * 文件二进制 — 业务侧读取本地 / OSS 文件成字节数组传入；provider 内部按需 base64 / multipart 上传。
     * 必填。
     */
    private byte[] fileBytes;

    /**
     * 文件类型 — provider 实现按此决定走 PDF 接口还是 image 接口。
     * 必填。
     */
    private FileType fileType;

    /**
     * 公式识别层级 — textin 专属参数（0=不识别公式 / 1=识别行内+块级 / 2=进一步表格 LaTeX 化）。
     * 默认 1。其他 provider 实现按各家映射。
     */
    private Integer formulaLevel = 1;

    /**
     * 是否返回切图 —"none"/"objects"/"page"。textin 专属；不需要切图传 "none" 省流量。
     */
    private String getImage = "objects";

    /**
     * 切图输出类型 — "url"（推荐，需 provider 提供 OSS）/"base64"。
     */
    private String imageOutputType = "url";

    /**
     * 业务自定义元数据 — 透传到日志 / 落库，便于关联业务上下文。
     */
    private Map<String, Object> metadata;

    /**
     * 单次调用超时 — null 走 OcrProperties 默认（B-007 配置 120s）。
     */
    private Duration timeout;

}
