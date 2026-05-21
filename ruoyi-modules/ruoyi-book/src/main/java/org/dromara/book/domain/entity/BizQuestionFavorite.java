package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目收藏实体（biz_question_favorite）。
 *
 * <p>表结构（V3 SQL）：
 * <pre>
 *   PRIMARY KEY (id) AUTO_INCREMENT
 *   UNIQUE KEY uk_user_question (user_id, question_id)   -- 每用户对每题至多 1 条
 *   KEY idx_user (user_id)
 * </pre>
 *
 * <p>folder_id 默认 0（默认收藏夹"我的试题"）— 本卡 V1 收藏夹 CRUD 未做，先全落 0。
 *
 * <p>toggle 语义（PRD §3.1 B-2）：
 * <ul>
 *   <li>POST 已存在 (user_id, question_id) → 物理删 → isFavorite=false</li>
 *   <li>POST 不存在                          → 物理插 → isFavorite=true</li>
 *   <li>DELETE                              → 物理删（幂等，删 0 行也返成功）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
@TableName("biz_question_favorite")
public class BizQuestionFavorite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键自增 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 收藏者 user_id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 题目 id
     */
    @TableField("question_id")
    private Long questionId;

    /**
     * 收藏夹 id（0=默认夹"我的试题"）
     */
    @TableField("folder_id")
    private Long folderId;

    /**
     * 收藏时间（DB 列 NOT NULL DEFAULT CURRENT_TIMESTAMP；MP 也兜底 INSERT 填）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
}
