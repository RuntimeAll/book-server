package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题卡题型徽标 VO（QuestionItemVo.patterns 元素）—— PRD-C-204。
 *
 * <p>page/list/select 二次批量回填该题挂接的题型（biz_question_pattern_rel JOIN
 * biz_question_pattern）。没题型的题返空数组。
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
public class PatternRefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** biz_question_pattern.id */
    private Long patternId;

    /** 题型名 */
    private String name;

    /** 是否主题型：1=主 / 0=次 */
    private Integer isPrimary;
}
