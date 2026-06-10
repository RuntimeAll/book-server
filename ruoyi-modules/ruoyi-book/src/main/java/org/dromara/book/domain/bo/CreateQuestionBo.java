package org.dromara.book.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PRD-C-009 — teacher 侧录题入参 BO（POST /teacher/question/create）。
 *
 * <p>路径 {@code POST /teacher/question/create}（@SaCheckLogin，挂 /teacher/ 走 MisiktEnvelopeAdvice）。
 * 由教师端工作台 / AI-Orchestrator（双头鉴权 ruoyi_username 登录后）调用，camelCase body + @Validated。
 *
 * <p>题面三要素长文本（{@code stem} 必 / {@code answer} / {@code analyze}）全部外置 biz_text_content
 * （content_type='S'/'A'/'E'），不写 biz_question.stem_text 老字段（PRD-B-006 后题面事实源走外置）。
 *
 * <p>AI 血缘 / 来源 + 5 维度打标列复用 B-012 V11 + C V16 已有列，零 DDL。dim3Skill / auxTags
 * 由 service 序列化为 JSON 文本落库（探针期不上 typeHandler，同 {@link UpdateLabelBo} 范式）。
 *
 * <p>🔴 绝不从 body 接收 {@code createBy / createUser / status / id} —— 服务端强制
 * （create_user/create_by = 登录老师；status='1' 已发布；id = 雪花）。前端传了也忽略。
 *
 * @author backend-dev
 */
@Data
public class CreateQuestionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ===== 必填 =====

    /** 题型：1=选择 / 4=填空 / 5=简答（misikt 真实 3 种），必填 → biz_question.question_type */
    @NotNull(message = "questionType 不能为空")
    private Integer questionType;

    /** 题干长文本（LaTeX / 纯文本），必填 → 外置 biz_text_content content_type='S' */
    @NotBlank(message = "stem 题干不能为空")
    private String stem;

    // ===== 可选：题面三要素长文本（外置，写时才建行） =====

    /** 答案长文本 → 外置 biz_text_content content_type='A'（非空才写） */
    private String answer;

    /** 解析长文本 → 外置 biz_text_content content_type='E'（非空才写） */
    private String analyze;

    // ===== 可选：biz_question 直列 =====

    /** 难度 1-4 星（字段名 difficult 非 difficulty）→ biz_question.difficult */
    private Integer difficult;

    /** 章节 / 知识点编码 → biz_question.subject_id */
    private String subjectId;

    /** 题干图（misikt 去 _url 入参）→ biz_question.stem_img_url */
    private String stemImg;

    /** 答案图 → biz_question.answer_img_url */
    private String answerImg;

    /** 解析图 → biz_question.explain_img_url */
    private String explainImg;

    /** 笔迹数据 → biz_question.file_bin_url */
    private String fileBin;

    /** 自由标签（逗号分隔）→ biz_question.free_tag */
    private String freeTag;

    /** 出处年份 → biz_question.exam_year */
    private String examYear;

    /** 出处试卷 ID → biz_question.exam_paper_id */
    private Long examPaperId;

    /** 出处试卷名 → biz_question.exam_paper_name */
    private String examPaperName;

    // ===== 可选：AI 血缘 / 来源（零 DDL，复用 B-012 V11+V16 已有列） =====

    /** 母题血缘 → biz_question.mother_question_id */
    private Long motherQuestionId;

    /** 变式关系（如 "AI-数值变式"）→ biz_question.variant_relation */
    private String variantRelation;

    /** 导入来源（默认 "AI-Orchestrator"）→ biz_question.import_source */
    private String importSource;

    /** 辅标签对象 → service 序列化为 JSON 文本 → biz_question.aux_tags（探针期 String 存） */
    private Map<String, Object> auxTags;

    // ===== 可选：5 维度打标（与 stem 一并入库时可带，复用 V16 列，同 UpdateLabelBo） =====

    /** ①知识点 ID → biz_question.dim1_kp_id */
    private String dim1KpId;

    /** ②题型 1选择/4填空/5解答/6证明 → biz_question.dim2_qtype */
    private Integer dim2Qtype;

    /** ③思维方法数组（service 序列化为 JSON 文本）→ biz_question.dim3_skill */
    private List<String> dim3Skill;

    /** ④难度 1-4 → biz_question.dim4_difficulty */
    private Integer dim4Difficulty;

    /** ⑤图形 / 情境结构指纹 → biz_question.dim5_structure */
    private String dim5Structure;

    /** AI 打标状态机：0未标 / 1AI已标 / 2已审核 / 3争议 → biz_question.label_status */
    private Integer labelStatus;

    /** AI 自评置信度 0-1 → biz_question.label_confidence */
    @DecimalMin(value = "0", message = "labelConfidence 不小于 0")
    @DecimalMax(value = "1", message = "labelConfidence 不大于 1")
    private BigDecimal labelConfidence;

    /** AI 模型名或人员 → biz_question.labeled_by */
    private String labeledBy;
}
