package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目标注实体（biz_question_annotation）。
 *
 * <p>PRD-B-012 标注维度地基：题目 × 维度 × 取值多对多明细表。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_question_annotation")
public class BizQuestionAnnotation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标注 ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 题目 ID
     */
    private Long questionId;

    /**
     * 维度 code（如 difficulty / knowledge / ability 等）
     */
    private String dimension;

    /**
     * 取值 code（字典 code）
     */
    private String valueCode;

    /**
     * 取值文本（冗余展示）
     */
    private String valueText;

    /**
     * 来源（human / ai / import 等）
     */
    private String source;

    /**
     * 置信度 0-100
     */
    private Integer confidence;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
