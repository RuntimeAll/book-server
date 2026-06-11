package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 导出配置（落 biz_export_record.options JSON 列，PRD-A-014）。
 *
 * <p>提交时由 {@link ExportPaperBo} 抽出序列化进库；Worker / retry 时反序列化拿回
 * variant + watermark + ids（题序）。ids 用 String 列表（雪花 id 字符串），与契约一致。
 *
 * @author backend-dev
 */
@Data
public class ExportOptions implements Serializable {

    /** 卷型：student / teacher / student_answers_appended */
    private String variant;

    /** 是否加水印 */
    private Boolean watermark;

    /** 导出题目顺序（string[] 雪花） */
    private List<String> ids;
}
