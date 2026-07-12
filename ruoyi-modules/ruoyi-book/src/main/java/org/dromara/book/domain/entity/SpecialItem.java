package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 专项内容项实体（复用 biz_shelf_item，PRD-003 C 位自建薄实体）。
 *
 * <p>{@code kind='question'} 题引用（question_id 指 biz_question.id）为本卡主用；explain 讲解块预留。
 * override_json = 书内改题副本（不改源题库）；🔴 本表无 meta 列，题目「留空高度」以保留键
 * {@code __gap}（整数 mm）内嵌 override_json，导出/回写时按 {@code __} 前缀剥离，不污染回写题库的副本。
 *
 * <p>本表无 create_by 等审计列，不继承 BaseEntity，时间列走 DB DEFAULT。
 *
 * @author codeplace-C PRD-003
 */
@Data
@TableName("biz_shelf_item")
public class SpecialItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 雪花主键（应用生成） */
    @TableId(value = "id")
    private Long id;

    /** 冗余书 id（书级统计/校验用） */
    private Long bookId;

    /** 所属节点 id（sec 或 tier） */
    private Long nodeId;

    /** 节点内排序 */
    private Integer seq;

    /** 类型：'question' 题引用 / 'explain' 讲解块 */
    private String kind;

    /** kind=question 时指 biz_question.id */
    private Long questionId;

    /** 书内改题副本 JSON（含保留键 __gap 存留空高度 mm） */
    private String overrideJson;

    /** kind=explain 时的讲解内容 JSON */
    private String explainJson;

    /** 认证计数：导出上课一次 +1 */
    private Integer usedCount;

    private Date createTime;

    private Date updateTime;
}
