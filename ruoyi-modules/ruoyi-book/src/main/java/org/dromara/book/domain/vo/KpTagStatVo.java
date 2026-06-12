package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识点标签候选池单项 VO（PRD-C-014 B1 T4）。
 *
 * <p>来源 biz_question_free_tag ⨝ biz_question_knowledge ⨝ biz_free_tag，
 * 某 kp 下高频标签：{@code id}=biz_free_tag.id / {@code name}=标签文本 / {@code count}=出现题数。
 *
 * @author backend-dev (PRD-C-014 B1)
 */
@Data
public class KpTagStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** biz_free_tag.id */
    private Long id;

    /** biz_free_tag.name */
    private String name;

    /** 该 kp 下出现题数 */
    private Long count;
}
