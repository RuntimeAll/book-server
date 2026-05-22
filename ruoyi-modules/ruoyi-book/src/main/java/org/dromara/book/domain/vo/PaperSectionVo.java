package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * POST /teacher/exam/paper/detail 响应内 sections 数组元素 VO（E 卡段②）。
 *
 * <p>大题分组：一个 paper 含多个 section（如"选择题/填空题/简答题"），每个 section
 * 内含若干 question。
 *
 * <p>字段映射：
 * <ul>
 *   <li>{@code sectionId} ← biz_paper_section.id</li>
 *   <li>{@code title} ← biz_paper_section.title（"选择题/填空题/解答题"）</li>
 *   <li>{@code sort} ← biz_paper_section.sort（**可能跳号**：1/3/4，不连续，必须 ORDER BY sort）</li>
 *   <li>{@code questions} ← 该 section 下所有题（已按 biz_paper_question.sort ASC 排序，跨 section 全局连续题号）</li>
 * </ul>
 *
 * @author backend-dev (E 卡段②)
 */
@Data
public class PaperSectionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 大题 id（biz_paper_section.id）
     */
    private Long sectionId;

    /**
     * 大题标题（"选择题" / "填空题" / "简答题"）
     */
    private String title;

    /**
     * 大题排序键（biz_paper_section.sort —— 可能跳号 1/3/4，section 间已 ORDER BY sort ASC）
     */
    private Integer sort;

    /**
     * 该大题下所有题（已按 biz_paper_question.sort ASC 排序，跨 section 全局连续题号）
     *
     * <p>复用 {@link PaperSourceQuestionVo} —— 含完整题字段（三图 / freeTags / questionKnowledges / 等）
     */
    private List<PaperSourceQuestionVo> questions;
}
