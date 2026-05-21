package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 试题筐实体（biz_question_basket）。
 *
 * <p>表结构：
 * <pre>
 *   PRIMARY KEY (user_id, question_id)   -- 复合主键，防重
 *   KEY idx_user_time (user_id, add_time)
 * </pre>
 *
 * <p>无 auto_increment 主键 — MyBatis-Plus BaseMapper.insert 仍可用（按字段全填），
 * 但加题走 INSERT IGNORE（复合主键防重）— 由 {@code BizQuestionBasketMapper.xml} 定制 SQL。
 *
 * <p>cancel / empty 走物理 DELETE — 用户态数据可物理删（Iron law §1.6）。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_question_basket")
public class BizQuestionBasket implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID（复合主键之一）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 题目 ID（复合主键之一）
     */
    @TableField("question_id")
    private Long questionId;

    /**
     * 加入时间
     */
    @TableField("add_time")
    private Date addTime;
}
