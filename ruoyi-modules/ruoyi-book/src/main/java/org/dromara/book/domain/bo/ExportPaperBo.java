package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 提交试卷导出任务入参（POST /teacher/export/paper，PRD-A-014）。
 *
 * <p>契约：{@code {paperId?, ids[], fileName, showAnswer, showExplain, watermark}}
 * <ul>
 *   <li>{@code paperId} — 来源试卷 id，可空（篮子直接导出无卷）。string（雪花），service 转 Long。</li>
 *   <li>{@code ids} — 导出题目顺序（来自预览），string[]（雪花），是 PDF 拼接的题序。</li>
 *   <li>{@code fileName} — 导出文件名（= 预览页可编辑标题）。</li>
 *   <li>{@code showAnswer} / {@code showExplain} — 是否随题附答案图 / 解析图，与预览页
 *       「显示答案 / 显示解析」勾选一致（2026-06-11 用户拍板去掉卷型三选一，导出=所见即所得）。</li>
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

    /** 导出文件名（= 预览页可编辑标题） */
    private String fileName;

    /** 是否随题附答案图（同预览「显示答案」） */
    private Boolean showAnswer;

    /** 是否随题附解析图（同预览「显示解析」） */
    private Boolean showExplain;

    /** 是否加水印 */
    private Boolean watermark;
}
