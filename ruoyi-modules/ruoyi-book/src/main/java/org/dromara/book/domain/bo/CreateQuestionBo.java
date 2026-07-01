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

/**
 * PRD-C-009 — teacher 侧录题入参 BO（POST /teacher/question/create）。
 *
 * <p>路径 {@code POST /teacher/question/create}（@SaCheckLogin，挂 /teacher/ 走 MisiktEnvelopeAdvice）。
 * 由教师端工作台 / AI-Orchestrator（双头鉴权 ruoyi_username 登录后）调用，camelCase body + @Validated。
 *
 * <p>题面三要素长文本（{@code stem} 必 / {@code answer} / {@code analyze}）全部外置 biz_text_content
 * （content_type='S'/'A'/'E'），不写 biz_question.stem_text 老字段（PRD-B-006 后题面事实源走外置）。
 *
 * <p>AI 血缘 / 来源 + 打标列（dim1/2/4/5）复用 B-012 V11 + C V16 已有列，零 DDL。
 * （🔴 V905 schema 收敛：free_tag / dim3_skill / aux_tags 列已 DROP，对应入参 freeTag / dim3Skill /
 * auxTags 一并移除；toolkit 旧 body 多带这几键也无害，Jackson 忽略未知属性。）
 *
 * <p>🔴 绝不从 body 接收 {@code createBy / createUser / status / id} —— 服务端强制
 * （create_user/create_by = 登录老师；id = 雪花）。前端传了也忽略。
 *
 * <p>🔴 PRD-A-021 R1a 起 {@code status} 从「服务端强制 '1'」改「可选透传」：缺省服务端落草稿态 '0'
 * （仅本人「我的题库」可见），调用方（toolkit）显式传 '1' 才直接落正式。createBy/createUser/id
 * 仍服务端强制忽略，status 是唯一新放开的可选透传字段。
 *
 * @author backend-dev
 */
@Data
public class CreateQuestionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ===== 必填 =====

    /** 题型：1=选择 / 4=填空 / 5=解答（misikt 真实 3 种），必填 → biz_question.question_type */
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

    /**
     * PRD-A-021 R1a 归属状态机：'0'=草稿（缺省，仅本人「我的题库」可见）/ '1'=正式（公共/全站可见）。
     * 缺省（空白）→ 服务端落 '0'；调用方（toolkit）显式传 '1' 才直接落正式。promote() 是 0→1 唯一公开动作。
     * → biz_question.status
     */
    private String status;

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

    // ===== 可选：5 维度打标（与 stem 一并入库时可带，复用 V16 列，同 UpdateLabelBo） =====

    /** ①知识点 ID → biz_question.dim1_kp_id */
    private String dim1KpId;

    /** ②题型 1选择/4填空/5解答/6证明 → biz_question.dim2_qtype */
    private Integer dim2Qtype;

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

    // ===== 可选：PRD-C-014 B1 扩字段（DNA 全维 / 挂接表，键名钉死，toolkit 按同一契约发） =====
    // 全部 optional：旧调用方不带时不破，对应行/列缺值时按各自规则跳过或留空。

    /**
     * 副知识点 ID 列表（≤3）→ biz_question_knowledge(is_primary=0) 各 1 行。
     * 与主 kp（dim1KpId）重复时跳过；越界（>3）由 W1 侧约束，BE 全量写池内项。
     */
    private List<Long> secondaryKpIds;

    /**
     * 标签列表（3~6）→ 三轨：biz_free_tag(exact 复用/新插) + biz_question_free_tag(position=下标)。
     * 空则整段跳过。
     */
    private List<String> tags;

    /**
     * 解法骨架（运算序列，【】标最难步）→ biz_question_ai.solution_skeleton（命名映射 skeleton→solution_skeleton）。
     */
    private String skeleton;

    /**
     * 场景（半开放，变式表皮必换项，≤64）→ biz_question_ai.scenario（命名映射 scene→scenario）。
     */
    private String scene;

    /**
     * 考察类型（怎么考：计算/证明/应用…闭集 10，≠考点）→ biz_question_ai.assessment_type（命名映射 examType→assessment_type）。
     */
    private String examType;

    /**
     * 难点/突破点列表（半开放）→ biz_question_ai.breakthrough_points(JSON 数组串)
     * + biz_question_ai.hard_point_count = 代码算 size()（不信调用方自报）。
     */
    private List<String> hardPoints;

    /**
     * 锚定用的 subject 节点 → biz_question_ai.anchor_id（DDL VARCHAR(20)，本字段按字符串接）。
     */
    private String anchorId;

    /**
     * 锚定存疑待人审 → biz_question_ai.need_anchor_review。
     */
    private Boolean needAnchorReview;

    /**
     * agent 抽取/生成依据 → biz_question_ai.reasoning。
     */
    private String reasoning;

    // ===== 可选：结构化网格块内容（PRD-A-015，§10.1 schema） =====

    /**
     * 结构化网格块 JSON（可空；非空时校验 §10.1 并落 biz_question_block，
     * question_id = 新建题 id，v=1，update_by = 登录老师）。
     */
    private String blockJson;
}
