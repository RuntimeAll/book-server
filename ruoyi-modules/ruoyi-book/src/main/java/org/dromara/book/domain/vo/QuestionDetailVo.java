package org.dromara.book.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * /teacher/question/select/{id} 详情 VO。
 *
 * <p>继承 {@link QuestionItemVo} 复用列表字段，新增详情专属字段：
 * <ul>
 *   <li>{@code answer} 答案文本（来源 biz_text_content content_type='A'，PRD-B-013 后唯一来源）</li>
 *   <li>{@code answerImg} 答案图 URL（DB 列 answer_img_url）</li>
 *   <li>{@code explain} 解析文本（来源 biz_text_content content_type='E'）</li>
 *   <li>{@code explainImg} 解析图 URL（DB 列 explain_img_url）</li>
 *   <li>{@code fileBin} 笔迹数据 URL（DB 列 file_bin_url）</li>
 *   <li>{@code questionStdKnowledges} 标准库标注知识点（source='S'，仅详情返）</li>
 * </ul>
 *
 * <p>🔴 PRD-B-013 减法：删除 3 个详情专属字段（详见 PRD-B-013 §scope.B 后端段）。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionDetailVo extends QuestionItemVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 答案文本（biz_text_content content_type='A' — PRD-B-013 减法后唯一来源）
     */
    private String answer;

    /**
     * 答案图 URL（DB 列 answer_img_url）
     */
    private String answerImg;

    /**
     * 解析文本（biz_text_content content_type='E'）
     */
    private String explain;

    /**
     * 解析图 URL（DB 列 explain_img_url）
     */
    private String explainImg;

    /**
     * 笔迹数据 URL（DB 列 file_bin_url）
     */
    private String fileBin;

    /**
     * 标准库标注知识点（source='S'，仅详情返）
     */
    private List<QuestionKnowledgeVo> questionStdKnowledges;

    // blockJson 已上移至基类 QuestionItemVo（page/list/select 三端均回填），此处不再重复声明。

    // ── PRD-A-015 属性编辑页 — 5 维 AI 打标 + 打标元数据（select 回填，供属性页查看/编辑）──
    /** ①知识点 ID（dim1_kp_id，关联 biz_subject 知识点层节点） */
    private String dim1KpId;
    /** ②题型重标（dim2_qtype：1选择/4填空/5解答/6证明） */
    private Integer dim2Qtype;
    // 🔴 C-100 B-converge 方案B：dim3Skill / auxTags 字段随 V905 DROP 列一并剥除（属性编辑页 C 线预期降级）
    /** ④难度重标 1-4（dim4_difficulty） */
    private Integer dim4Difficulty;
    /** ⑤图形/情境结构指纹（dim5_structure） */
    private String dim5Structure;
    /** AI 自评置信度 0-1（label_confidence） */
    private BigDecimal labelConfidence;
    /** 打标者：AI 模型名或人员（labeled_by） */
    private String labeledBy;
    /** 打标时间（labeled_at） */
    private Date labeledAt;

    // ── PRD-A-015 属性编辑页 — N1 高级属性列（biz_question 业务列，select 回填）──
    /** 标准分值（base_score） */
    private BigDecimal baseScore;
    /** 导入来源（import_source：manual/textin/misikt/my-clone/ai-orchestrator） */
    private String importSource;
    /** 地域编码（region_code，国标行政区划） */
    private String regionCode;
    /** 来源类型（source_type：1中考真题/2模拟/3期末/4月考/5单元/6自编/9其他） */
    private Integer sourceType;
    /** 母题指针（mother_question_id，自关联血缘源；雪花 Long，BigNumberSerializer 序列化为 string） */
    private Long motherQuestionId;
    /** 变式关系（variant_relation：数值变式/情境变式/结构变式/同源） */
    private String variantRelation;
    /** 标注 schema 版本（annotate_version） */
    private Integer annotateVersion;
    /** 标注完整度（annotate_status：0未标/1已标全/2部分） */
    private Integer annotateStatus;

    // ── PRD-C-204 题库核实增维 — 全维 DNA 嵌套对象（来源 biz_question_ai 最新版一行；无行则 null）──
    /**
     * 全维 DNA（solutionSkeleton / scenario / assessmentType / hardPoints / tags / 锚定 / 打标元 …）。
     *
     * <p>来源 biz_question_ai 按 question_id 取最新 annotate_version 一行。ai 表无行 → {@code null}（FE 自行兜底）。
     * JSON 串列原样吐 String，FE 自己 parse（详见 {@link QuestionDnaVo}）。
     */
    private QuestionDnaVo dna;

    /**
     * 命中的解题模型列表（biz_question_model JOIN biz_solution_model，主模型在前）。
     *
     * <p>该题在 biz_question_model 无行 → 空列表。高级属性页「解题模型」区只读展示。
     */
    private List<QuestionModelVo> models;

    // ── PRD-C-213 私有题池增列（备课包段内题显示★星级与来源）──
    /** 星级：'1' ★ / '2' ★★ / '3' ★★★（biz_question.star_level，专项卷分层；无则 null）。 */
    private String starLevel;

    /** 素材源（biz_question.source_ref，如"学而思四年级P25"；无则 null）。 */
    private String sourceRef;

    /** 专项名（biz_question.topic_tag，自由文本字典化后置；无则 null）。 */
    private String topicTag;

    // examYear / examPaperName / labelStatus 已在基类 QuestionItemVo 声明（PRD-C-204 selectById 一并回填）。
}
