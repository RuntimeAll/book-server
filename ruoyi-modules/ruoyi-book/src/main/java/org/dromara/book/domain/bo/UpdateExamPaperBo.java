package org.dromara.book.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * PRD-A-005 T3 — 试卷编辑入参 BO（POST /teacher/exam/paper/update）。
 *
 * <p>来源：FE 试卷编辑页 papers/edit.vue —— 用户重排 / 删 / 增题后整卷提交。
 *
 * <p>语义：{@link #questions} 是该卷编辑后的 **最终完整题集**（按 sort 升序），
 * 后端事务内 = 删该 paperId 旧 biz_paper_question 全部行 → 按本列表批量重插。
 * name / paperCategoryId 可选，传则更新卷元信息。
 *
 * @author backend-dev (PRD-A-005 T3)
 */
@Data
public class UpdateExamPaperBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 试卷 id（biz_paper.id） — 必填 */
    @NotNull(message = "试卷ID不能为空")
    private Long paperId;

    /** 试卷名称 — 可选，传则更新 */
    private String name;

    /** 试卷分类 ID — 可选，传则更新 */
    private String paperCategoryId;

    /** 编辑后的最终完整题集（按 sort 升序），至少 1 题 */
    @NotEmpty(message = "题目列表不能为空")
    @Valid
    private List<UpdateExamPaperQuestionBo> questions;

    /**
     * 试卷内单题项（biz_paper_question 一行）。
     */
    @Data
    public static class UpdateExamPaperQuestionBo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 题目 id（biz_paper_question.question_id） — 必填 */
        @NotNull(message = "题目ID不能为空")
        private Long questionId;

        /** 大题分组 id（biz_paper_question.section_id） — 必填，题挂在该 section 下 */
        @NotNull(message = "大题分组ID不能为空")
        private Long sectionId;

        /** 卷内排序（biz_paper_question.sort） — 必填 */
        @NotNull(message = "题目排序不能为空")
        private Integer sort;

        /** 分值（biz_paper_question.score） — 可选，缺省按 0 处理 */
        private BigDecimal score;
    }
}
