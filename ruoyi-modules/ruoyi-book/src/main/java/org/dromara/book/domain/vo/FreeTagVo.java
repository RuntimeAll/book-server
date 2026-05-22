package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目 freeTag 关联 VO（X 卡 / freeTag 字典化）。
 *
 * <p>来源 biz_question_free_tag JOIN biz_free_tag：
 * <ul>
 *   <li>{@code id} — biz_free_tag.id（字典主键）</li>
 *   <li>{@code name} — biz_free_tag.name（tag 文本）</li>
 *   <li>{@code position} — biz_question_free_tag.position（0/1/2/3/4，
 *       同一题内的位置，决定 FE 颜色 / 尺寸约定 — BE 只透传，颜色 FE 算）</li>
 * </ul>
 *
 * <p>跟 {@code QuestionItemVo.freeTag}（原始字符串字段）并存：
 * 老字段不删，保证向下兼容；新字段 {@code freeTags} 是结构化数组。
 *
 * @author backend-dev (X 卡段②)
 */
@Data
public class FreeTagVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * biz_free_tag.id
     */
    private Long id;

    /**
     * biz_free_tag.name
     */
    private String name;

    /**
     * 同题内位置（biz_question_free_tag.position），0 起算
     */
    private Integer position;
}
