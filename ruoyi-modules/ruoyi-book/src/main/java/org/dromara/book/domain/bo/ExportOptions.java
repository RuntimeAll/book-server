package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 导出配置（落 biz_export_record.options JSON 列，PRD-A-014）。
 *
 * <p>提交时由 {@link ExportPaperBo} 抽出序列化进库；Worker / retry 时反序列化拿回
 * showAnswer/showExplain + watermark + ids（题序）。ids 用 String 列表（雪花 id 字符串），与契约一致。
 *
 * @author backend-dev
 */
@Data
public class ExportOptions implements Serializable {

    /**
     * 旧卷型字段（student / teacher / student_answers_appended）。
     * 2026-06-11 契约改为 showAnswer/showExplain 双开关后不再写入，仅为反序列化存量
     * options JSON（retry 旧记录）保留；读取侧见 ExportPdfWorker 的 variant→双开关映射。
     */
    private String variant;

    /** 是否随题附答案图（同预览「显示答案」） */
    private Boolean showAnswer;

    /** 是否随题附解析图（同预览「显示解析」） */
    private Boolean showExplain;

    /** 是否加水印 */
    private Boolean watermark;

    /** 导出题目顺序（string[] 雪花） */
    private List<String> ids;
}
