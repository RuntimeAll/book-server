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
 * 题目个人备注实体（biz_question_note）。
 *
 * <p>表结构（V3 SQL）：
 * <pre>
 *   PRIMARY KEY (id) AUTO_INCREMENT
 *   UNIQUE KEY uk_user_question (user_id, question_id)   -- 每用户对每题至多 1 条
 *   KEY idx_user (user_id)
 *   content TEXT NOT NULL
 *   create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
 *   update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>upsert 语义（PRD §3.1 B-5）：
 * <ul>
 *   <li>POST 已存在 (user_id, question_id) → UPDATE content + update_time</li>
 *   <li>POST 不存在                          → INSERT</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
@TableName("biz_question_note")
public class BizQuestionNote implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键自增 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 备注归属 user_id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 题目 id
     */
    @TableField("question_id")
    private Long questionId;

    /**
     * 备注内容（TEXT，富文本/纯文本均可）
     */
    @TableField("content")
    private String content;

    /**
     * 首次写入时间（DB 列 NOT NULL DEFAULT CURRENT_TIMESTAMP；MP 也兜底 INSERT 填）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 最近修改时间（DB 列 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP；
     * MP 也兜底 INSERT_UPDATE 填，避免 upsert 二步时间不一致）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
