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
 * 题目-知识点 M:N 实体（biz_question_knowledge）。
 *
 * <p>U/S 双轨：
 * <ul>
 *   <li>{@code source='U'} 用户标注：列表 + 详情都返</li>
 *   <li>{@code source='S'} 标准库标注：仅详情返</li>
 * </ul>
 *
 * <p>🔴 PRD-B-013 减法：biz_subject 删 2 个媒体字段，本实体不变（关联表无此字段）。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_question_knowledge")
public class BizQuestionKnowledge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long questionId;

    /**
     * 关联 biz_subject.id（叶子或任意层级）
     */
    private String knowledgeId;

    /**
     * U=用户标注 / S=标准库标注
     */
    private String source;

    /**
     * 1=主考点 / 0=副考点（V905 schema 收敛新增 is_primary）。
     *
     * <p>主副同属一套知识体系 biz_subject，仅此标记区分；副 kp（原 biz_question_secondary_kp）
     * 已并入本表 is_primary=0。
     */
    private Integer isPrimary;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
