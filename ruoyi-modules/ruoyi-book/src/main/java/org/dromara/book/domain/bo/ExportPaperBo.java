package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 提交试卷导出任务入参（POST /teacher/export/paper，PRD-A-014）。
 *
 * <p>契约：{@code {paperId?, ids[], fileName, variant, watermark}}
 * <ul>
 *   <li>{@code paperId} — 来源试卷 id，可空（篮子直接导出无卷）。string（雪花），service 转 Long。</li>
 *   <li>{@code ids} — 导出题目顺序（来自预览），string[]（雪花），是 PDF 拼接的题序。</li>
 *   <li>{@code fileName} — 导出文件名（弹窗里填的，默认 = 卷名）。</li>
 *   <li>{@code variant} — 卷型：student 学生卷 / teacher 教师卷（题后跟答案+解析）/
 *       student_answers_appended 学生卷+答案集中附后。</li>
 *   <li>{@code watermark} — 是否加水印（老师名 + 脱敏手机号平铺斜排）。</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
public class ExportPaperBo implements Serializable {

    /** 来源试卷 id（可空，string 雪花） */
    private String paperId;

    /** 导出题目顺序（来自预览，string[] 雪花） */
    private List<String> ids;

    /** 导出文件名（默认 = 卷名） */
    private String fileName;

    /** 卷型：student / teacher / student_answers_appended */
    private String variant;

    /** 是否加水印 */
    private Boolean watermark;
}
