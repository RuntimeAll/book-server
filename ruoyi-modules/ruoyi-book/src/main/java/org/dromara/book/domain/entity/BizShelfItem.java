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
 * 书架·内容项（biz_shelf_item，PRD-002）。kind=explain 讲解块（书自持 explain_json，D2）| question 题引用（question_id + 可选 override_json，D3/D4）。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_shelf_item")
public class BizShelfItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 冗余书 id（书级统计/校验用） */
    private Long bookId;

    private Long nodeId;

    private Integer seq;

    /** explain 讲解块 / question 题引用 */
    private String kind;

    /** kind=question 时指 biz_question.id */
    private Long questionId;

    /** 书内改题副本（D3；回写题库=直接覆盖原题 D4） */
    private String overrideJson;

    /** kind=explain 时的讲解内容（书自持，D2） */
    private String explainJson;

    /** 源书页码（PRD-006 反查回填；NULL=待定位） */
    private Integer sourcePage;

    /** 审核置信度 0-100（PRD-006 增强 2026-07-21）：>=90 可速过 / 60-89 常规 / <60 重点审；NULL=未评 */
    private Integer confidence;

    /** 认证计数：导出上课一次 +1（PRD-003 D6 回写） */
    private Integer usedCount;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
