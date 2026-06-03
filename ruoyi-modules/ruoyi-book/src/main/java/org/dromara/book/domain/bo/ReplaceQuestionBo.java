package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * PRD-A-007 T1 — 换一题入参 BO（POST /teacher/question/replace）。
 *
 * <p>语义：用 {@link #currentQuestionId} 确定被替换题的 subject/考点/题型，
 * 在 {@link #excludeIds}（本卷已有全部题 id）之外搜一道同类候选题。
 *
 * @author backend-dev (PRD-A-007 T1)
 */
@Data
public class ReplaceQuestionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前要被替换的题目 id — 必填 */
    @NotNull(message = "当前题目ID不能为空")
    private Long currentQuestionId;

    /**
     * 本卷已有全部题 id（含当前题自身）— 排除列表，换来的题不能在其中。
     * 可空（空则只排除 currentQuestionId 自身）。
     */
    private List<Long> excludeIds;
}
