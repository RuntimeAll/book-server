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
 * DNA 编辑经验层留痕实体（biz_dna_edit_log，只累计不消费）。
 *
 * <p>表结构（V910 SQL）：
 * <pre>
 *   PRIMARY KEY (id) AUTO_INCREMENT
 *   KEY idx_teacher_created (teacher_id, created_at)
 *   target_id         VARCHAR(64) NULL   -- 母题/变式 id 或 thread_id
 *   edit_kind         VARCHAR(16) NOT NULL -- dna维 / 图修正
 *   dim               VARCHAR(32) NULL
 *   before_val        TEXT NULL
 *   after_val         TEXT NULL
 *   correction_prompt TEXT NULL
 *   committed         TINYINT(1) NOT NULL DEFAULT 0
 *   created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>无 tenant_id 列（照 biz_question_note 风格），mapper 走 {@code BizBaseMapper} 忽略租户拦截。
 * 只写 + 可选 list，无 update_time（仅 created_at）。
 *
 * @author PRD-C-100 B4
 */
@Data
@TableName("biz_dna_edit_log")
public class BizDnaEditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键自增 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 操作老师 user_id
     */
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 母题/变式 id 或 thread_id
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 编辑类型（dna维 / 图修正）
     */
    @TableField("edit_kind")
    private String editKind;

    /**
     * 改的哪一维（dna维时）
     */
    @TableField("dim")
    private String dim;

    /**
     * 改前值
     */
    @TableField("before_val")
    private String beforeVal;

    /**
     * 改后值
     */
    @TableField("after_val")
    private String afterVal;

    /**
     * 图修正提示词
     */
    @TableField("correction_prompt")
    private String correctionPrompt;

    /**
     * 是否入库（1=已入库）
     */
    @TableField("committed")
    private Integer committed;

    /**
     * 创建时间（DB 列 NOT NULL DEFAULT CURRENT_TIMESTAMP；MP 也兜底 INSERT 填）
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;
}
