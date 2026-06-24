package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 题目 DNA 维度 VO（PRD-C-204 题库核实增维）—— 镜像 biz_question_ai 的全 DNA 维。
 *
 * <p>挂在 {@link QuestionDetailVo#getDna()} 作嵌套对象返回。来源 = biz_question_ai 按
 * question_id 取最新 annotate_version 的一行；该题在 ai 表无行时 {@code dna=null}（不抛）。
 *
 * <p>🔴 JSON 串列（hardPoints / breakthroughPoints / tags / mathThoughts / parametricSlots /
 * modelingFrame / conditions / variationProfile）一律以 **原样 String** 吐出（数据库 JSON 列里
 * 存的就是 JSON 数组/对象串），FE 自行 {@code JSON.parse}，BE 不强转、不二次序列化（避免双重转义）。
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
public class QuestionDnaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 标注 schema 版本（biz_question_ai.annotate_version） */
    private Integer annotateVersion;

    /** 解法骨架（运算序列，【】标最难步）solution_skeleton */
    private String solutionSkeleton;

    /** 场景（半开放，变式表皮必换项）scenario */
    private String scenario;

    /** 考察类型（怎么考：计算/证明/应用…闭集）assessment_type */
    private String assessmentType;

    /** 难点个数（0=基础题）hard_point_count */
    private Integer hardPointCount;

    /** 突破点/难点（JSON 数组串，原样吐）breakthrough_points */
    private String breakthroughPoints;

    /** 难点（JSON 数组串，原样吐；与 breakthroughPoints 并存）hard_points */
    private String hardPoints;

    /** 自由标签（JSON 数组串，原样吐）tags */
    private String tags;

    /** 数学思想/方法标签（JSON 数组串，原样吐）math_thoughts */
    private String mathThoughts;

    /** 参数化槽位（JSON 数组串，原样吐）parametric_slots */
    private String parametricSlots;

    /** 建模框架（JSON 串，原样吐）modeling_frame */
    private String modelingFrame;

    /** 条件集（JSON 串，原样吐）conditions */
    private String conditions;

    /** 变式画像（JSON 串，原样吐）variation_profile */
    private String variationProfile;

    /** 验证种类 verify_kind */
    private String verifyKind;

    /** DNA 类型 dna_type */
    private String dnaType;

    /** 难度判定理由 difficulty_reason */
    private String difficultyReason;

    /** 锚定用的 subject 节点 anchor_id */
    private String anchorId;

    /** agent 抽取/生成依据 reasoning */
    private String reasoning;

    /** 锚定置信 0~1 confidence */
    private BigDecimal confidence;

    /** 锚定存疑待人审（0/1）need_anchor_review */
    private Integer needAnchorReview;

    /** 打标状态：1AI已标 / 2已审 / 3争议 label_status */
    private Integer labelStatus;

    /** AI 模型名或人员 labeled_by */
    private String labeledBy;
}
