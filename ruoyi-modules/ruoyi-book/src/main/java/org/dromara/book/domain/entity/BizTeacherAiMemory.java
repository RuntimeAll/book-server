package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 老师全局 AI 记忆实体（biz_teacher_ai_memory）。
 *
 * <p>表结构（V909 SQL）：
 * <pre>
 *   PRIMARY KEY (id) AUTO_INCREMENT
 *   KEY idx_teacher_type (teacher_id, mem_type)
 *   KEY idx_teacher_enabled (teacher_id, enabled)
 *   mem_type    VARCHAR(16)  NOT NULL                 -- 偏好 / 纠正 / 习惯
 *   mem_key     VARCHAR(64)  NOT NULL
 *   mem_value   VARCHAR(512) NOT NULL
 *   source      VARCHAR(8)   NOT NULL DEFAULT '手填'  -- 自动 / 手填
 *   enabled     TINYINT(1)   NOT NULL DEFAULT 1       -- 停用不注入 prompt
 *   confidence  DECIMAL(4,3) NULL
 *   create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
 *   update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>无 tenant_id 列（照 biz_question_note 风格），mapper 走 {@code BizBaseMapper} 忽略租户拦截。
 *
 * @author PRD-C-100 B4
 */
@Data
@TableName("biz_teacher_ai_memory")
public class BizTeacherAiMemory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键自增 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属老师 user_id
     */
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 记忆类型（偏好 / 纠正 / 习惯）
     */
    @TableField("mem_type")
    private String memType;

    /**
     * 记忆键（如「常教年级」「教材版本」）
     */
    @TableField("mem_key")
    private String memKey;

    /**
     * 记忆值
     */
    @TableField("mem_value")
    private String memValue;

    /**
     * 来源（自动 / 手填）
     */
    @TableField("source")
    private String source;

    /**
     * 启停（1=启用，0=停用不注入 prompt）
     */
    @TableField("enabled")
    private Integer enabled;

    /**
     * 置信度（自动写入时可带）
     */
    @TableField("confidence")
    private BigDecimal confidence;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 首次写入时间（DB 列 NOT NULL DEFAULT CURRENT_TIMESTAMP；MP 也兜底 INSERT 填）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 最近修改时间（DB 列 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP；
     * MP 也兜底 INSERT_UPDATE 填）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
