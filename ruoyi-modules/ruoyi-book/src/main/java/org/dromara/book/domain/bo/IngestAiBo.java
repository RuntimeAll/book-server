package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 打标 upsert 入参（POST /teacher/ingest/ai/{questionId}）—— PRD-C-204 B1。
 *
 * <p>写 biz_question_ai（uk_q_ver=question_id+annotate_version upsert），JSON 字段
 * 由 service 序列化为 JSON 串入库。同步回写 biz_question.label_status/labeled_by/annotate_status=1。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
public class IngestAiBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 标注版本（缺省 1，配合 uk_q_ver upsert） */
    private Integer annotateVersion;

    /** 锚知识点 → anchor_id */
    private String anchorKpId;

    /** 锚定置信 0~1 → confidence */
    private BigDecimal anchorConfidence;

    /** 锚定存疑待审 0/1 → need_anchor_review */
    private Integer needAnchorReview;

    /** 打标状态 → label_status */
    private Integer labelStatus;

    /** 模型名或人员 → labeled_by */
    private String labeledBy;

    /** 考察类型 → assessment_type */
    private String assessmentType;

    /** 难点（JSON 数组）→ hard_points + hard_point_count(size) */
    private List<Object> hardPoints;

    /** 突破点（JSON 数组）→ breakthrough_points */
    private List<Object> breakthroughPoints;

    /** 自由标签（JSON 数组）→ tags */
    private List<Object> tags;

    /** 验证种类 → verify_kind */
    private String verifyKind;

    /** DNA 类型 → dna_type */
    private String dnaType;

    /** 解法骨架 → solution_skeleton */
    private String solutionSkeleton;

    /** 参数化槽位（JSON）→ parametric_slots */
    private List<Object> parametricSlots;

    /** 建模框架（JSON）→ modeling_frame */
    private Object modelingFrame;

    /** 场景 → scenario */
    private String scenario;

    /** 条件集（JSON）→ conditions */
    private Object conditions;

    /** 变式画像（JSON）→ variation_profile */
    private Object variationProfile;
}
