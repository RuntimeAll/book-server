package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题↔题型关联实体（biz_question_pattern_rel）—— PRD-C-204 题型独立可查维度。
 *
 * <p>一道题可属多个题型，一个题型可挂多道题。UNIQUE(question_id, pattern_id) 幂等。
 * is_primary 标主题型（1=主 / 0=次）。
 *
 * <p>无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
@TableName("biz_question_pattern_rel")
public class BizQuestionPatternRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（DDL BIGINT AUTO_INCREMENT） */
    @TableId(value = "id")
    private Long id;

    /** 题目 id（biz_question.id，NOT NULL） */
    private Long questionId;

    /** 题型 id（biz_question_pattern.id，NOT NULL） */
    private Long patternId;

    /** 是否主题型：1=主 / 0=次，NOT NULL（DDL DEFAULT 1） */
    private Integer isPrimary;

    /** 来源：'main'=书 / 'AI'，NOT NULL */
    private String source;

    /** 创建时间（DDL DEFAULT CURRENT_TIMESTAMP） */
    private Date createTime;
}
