package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题型目录列表项 VO（GET /teacher/question/patterns）—— PRD-C-204。
 *
 * <p>给前端「点知识点 → 题型下拉收窄 / 虚拟子层」用。来源 biz_question_pattern，
 * 按 anchor_subject_id + sort 排。
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
public class PatternVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** biz_question_pattern.id */
    private Long id;

    /** 题型名 */
    private String name;

    /** 锚知识点/小节（biz_subject.id） */
    private String anchorSubjectId;

    /** 排序 */
    private Integer sort;
}
