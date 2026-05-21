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
 *   <li>{@code source='U'} 用户标注：列表 + 详情都返；含 {@code knowledgeVideo}</li>
 *   <li>{@code source='S'} 标准库标注：仅详情返；不含 {@code knowledgeVideo}</li>
 * </ul>
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

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
