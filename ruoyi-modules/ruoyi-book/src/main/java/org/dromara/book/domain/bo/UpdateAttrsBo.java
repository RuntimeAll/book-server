package org.dromara.book.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * PRD-A-015 属性编辑页回写入参 BO（{@code POST /teacher/question/update-attrs}）。
 *
 * <p>「老师属性编辑页」专用，覆盖 基础属性 + 5维 AI 打标 + N1 高级列。与排版({@code update}=blockJson)、
 * AI 打标({@code update-label}=LangGraph)分工。
 *
 * <p>🔴 语义：除 questionId 外**全部字段可选**，service 只回写「传了的（非 null）」列
 * （LambdaUpdateWrapper 条件 set），不传的列保持原值——避免局部保存清空其它维度。
 * 故无法用本端点把某列「置空」（探针期可接受；需清空走专门逻辑）。
 *
 * <p>权限：service 内 owner||isSuperAdmin 校验（本人题或超管才可改）。
 */
@Data
public class UpdateAttrsBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 题目 ID，必填 */
    @NotNull(message = "questionId 不能为空")
    private Long questionId;

    // ── 基础属性 ──
    /** 章节/知识点编码（subject_id，biz_subject 节点 id） */
    private String subjectId;
    /** 题型 1选择/4填空/5解答（question_type） */
    private Integer questionType;
    /** 难度 1-4 星（difficult） */
    private Integer difficult;

    // ── 5 维 AI 打标 ──
    /** ①知识点 ID（dim1_kp_id） */
    private String dim1KpId;
    /** ②题型 1选择/4填空/5解答/6证明（dim2_qtype） */
    private Integer dim2Qtype;
    // 🔴 C-100 B-converge 方案B：dim3Skill / auxTags 字段随 V905 DROP 列一并剥除（属性编辑页 C 线预期降级）
    /** ④难度 1-4（dim4_difficulty） */
    private Integer dim4Difficulty;
    /** ⑤图形/情境结构指纹（dim5_structure） */
    private String dim5Structure;
    /** 标注状态机：0未标/1AI已标/2已审核/3争议（label_status） */
    private Integer labelStatus;
    /** AI 自评置信度 0-1（label_confidence） */
    @DecimalMin(value = "0", message = "labelConfidence 不小于 0")
    @DecimalMax(value = "1", message = "labelConfidence 不大于 1")
    private BigDecimal labelConfidence;
    /** 打标者：AI 模型名或人员（labeled_by） */
    private String labeledBy;

    // ── N1 高级属性列 ──
    /** 标准分值（base_score） */
    private BigDecimal baseScore;
    /** 来源类型 1中考真题/2模拟/3期末/4月考/5单元/6自编/9其他（source_type） */
    private Integer sourceType;
    /** 地域编码（region_code，国标行政区划） */
    private String regionCode;
    /** 变式关系 数值变式/情境变式/结构变式/同源（variant_relation） */
    private String variantRelation;
    /** 母题指针（mother_question_id，雪花 Long） */
    private Long motherQuestionId;
    /** 标注完整度 0未标/1已标全/2部分（annotate_status） */
    private Integer annotateStatus;

    // ── PRD-C-204 题库核实增维 — 主表出处列（条件 set 进 biz_question）──
    /** 出处年份（exam_year） */
    private String examYear;
    /** 出处试卷名（exam_paper_name） */
    private String examPaperName;

    // ── PRD-C-204 题库核实增维 — DNA 维（upsert biz_question_ai；条件 set，null 不改）──
    /**
     * 解法骨架（solution_skeleton）。
     *
     * <p>🔴 含数学 {@code < > } 文本 — 端点 {@code /teacher/question/update-attrs} 必须挂进
     * application.yml {@code xss.excludeUrls}，否则 XssFilter 静默剥标签腐蚀数学。
     */
    private String solutionSkeleton;
    /** 场景（scenario） */
    private String scenario;
    /** 考察类型（assessment_type） */
    private String assessmentType;
    /** 难点（数组入参 → 存 JSON 串到 biz_question_ai.hard_points + breakthrough_points） */
    private List<String> hardPoints;
    /** 自由标签 DNA（数组入参 → 存 JSON 串到 biz_question_ai.tags） */
    private List<String> tags;
}
