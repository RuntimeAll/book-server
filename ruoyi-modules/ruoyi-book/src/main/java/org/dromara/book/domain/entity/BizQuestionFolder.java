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
 * 题目收藏夹实体（biz_question_folder）。
 *
 * <p>表结构：
 * <pre>
 *   id          bigint PK AUTO_INCREMENT
 *   user_id     bigint NOT NULL                 -- 所属用户（数据隔离一律以 LoginHelper.getUserId() 为准）
 *   name        varchar(64) NOT NULL            -- 夹名（唯一可改字段）
 *   pid         bigint DEFAULT 0                 -- 父夹 id（0=根）
 *   sort        int DEFAULT 0
 *   create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 只读展示
 * </pre>
 *
 * <p>PRD-A-005 收尾（B-收藏夹 CRUD）—— 表已存在、空表，不建表不写迁移。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_question_folder")
public class BizQuestionFolder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键自增 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 user_id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 收藏夹名称（唯一可改字段）
     */
    @TableField("name")
    private String name;

    /**
     * 父夹 id（0=根）
     */
    @TableField("pid")
    private Long pid;

    /**
     * 排序
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 创建时间（DB NOT NULL DEFAULT CURRENT_TIMESTAMP；只读展示，仅 INSERT 填）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
}
