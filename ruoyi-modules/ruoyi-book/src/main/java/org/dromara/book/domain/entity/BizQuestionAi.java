package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * AI 派生表实体（biz_question_ai）—— PRD-C-014 B1 新建。
 *
 * <p>严格按 V905 收敛后 16 列（{@code claude-code-sign/25-题库schema-收敛DDL.sql} / V905 迁移）：
 * 只装「主表已有的不重复」的 AI 专属字段 —— 变式基因（解法骨架 / 场景 / 考察类型）、
 * 难点（突破点 + 个数）、锚定溯源审计、打标元。1 行/题/version，UNIQUE(question_id, annotate_version)。
 *
 * <p>命名映射在 BO→实体层做（QuestionServiceImpl.create）：
 * {@code skeleton→solution_skeleton}、{@code scene→scenario}、{@code examType→assessment_type}、
 * {@code hardPoints→breakthrough_points(JSON 数组串) + hard_point_count(代码算 size())}。
 *
 * <p>{@code breakthrough_points} DB 列是 JSON，本实体以 String 承载（服务端构造好 JSON 数组串写入），
 * 读取场景暂无（B1 只写不读 ai 表）。
 *
 * <p>biz_question_ai 无 tenant_id 列 —— 通过 {@link org.dromara.book.mapper.BizQuestionAiMapper}
 * 类级 {@code @InterceptorIgnore(tenantLine="true")} 关多租户拦截器。
 *
 * @author backend-dev (PRD-C-014 B1)
 */
@Data
@TableName("biz_question_ai")
public class BizQuestionAi implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键自增（DDL BIGINT AUTO_INCREMENT，非雪花） */
    @TableId(value = "id")
    private Long id;

    /** 源 biz_question.id */
    private Long questionId;

    /** 标注 schema 版本（DDL NOT NULL DEFAULT 1） */
    private Integer annotateVersion;

    /** 解法骨架（运算序列，【】标最难步）← BO.skeleton */
    private String solutionSkeleton;

    /** 场景（半开放，变式表皮必换项，≤64）← BO.scene */
    private String scenario;

    /** 考察类型（怎么考：计算/证明/应用…闭集 10，≠考点）← BO.examType */
    private String assessmentType;

    /** 难点个数（0=基础题）= 代码算 BO.hardPoints.size()，不信调用方自报 */
    private Integer hardPointCount;

    /** 突破点/难点（JSON 数组串）← BO.hardPoints */
    private String breakthroughPoints;

    /** 锚定用的 subject 节点 ← BO.anchorId */
    private String anchorId;

    /** 锚定置信 0~1 ← BO.labelConfidence */
    private BigDecimal confidence;

    /** 锚定存疑待人审（DDL TINYINT(1) DEFAULT 0）← BO.needAnchorReview */
    private Integer needAnchorReview;

    /** agent 抽取/生成依据 ← BO.reasoning */
    private String reasoning;

    /** 打标状态：1AI已标 / 2已审 / 3争议 ← BO.labelStatus */
    private Integer labelStatus;

    /** AI 模型名或人员 ← BO.labeledBy */
    private String labeledBy;

    /** 打标时间 */
    private Date labeledAt;

    /** 创建时间（DDL 无 fill，服务端显式 set now） */
    private Date createTime;
}
