package org.dromara.book.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * PRD-C-007 T1 — 题目 5 维度打标回写入参 BO。
 *
 * <p>路径 {@code POST /teacher/question/update-label}（@SaCheckLogin，挂 /teacher/ 走 MisiktEnvelopeAdvice）。
 *
 * <p>由 teacher-copilot LangGraph persist 节点调用：仅 UPDATE 打标列（dim1/2/4/5 / label_*），
 * 不碰题干/答案。{@code labeledAt} 不在 body —— 服务端取 now。
 *
 * <p>🔴 V905 schema 收敛：dim3_skill / aux_tags 列已 DROP（思维方法砍维 / 血缘并入 ai 表），
 * 故移除 dim3Skill / auxTags 入参；toolkit 旧 body 多带这两键也无害（Jackson 忽略未知属性）。
 *
 * @author backend-dev
 */
@Data
public class UpdateLabelBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 题目 ID（biz_question.id），必填 */
    @NotNull(message = "questionId 不能为空")
    private Long questionId;

    /** ①知识点 ID（dim1_kp_id） */
    private String dim1KpId;

    /** ②题型 1选择/4填空/5解答/6证明（dim2_qtype） */
    private Integer dim2Qtype;

    /** ④难度 1-4（dim4_difficulty） */
    private Integer dim4Difficulty;

    /** ⑤图形/情境结构指纹（dim5_structure） */
    private String dim5Structure;

    /** AI 打标状态机：1=AI已标 / 2=已审核（label_status），必填 */
    @NotNull(message = "labelStatus 不能为空")
    private Integer labelStatus;

    /** AI 自评置信度 0-1（label_confidence） */
    @DecimalMin(value = "0", message = "labelConfidence 不小于 0")
    @DecimalMax(value = "1", message = "labelConfidence 不大于 1")
    private BigDecimal labelConfidence;

    /** AI 模型名或人员（labeled_by） */
    private String labeledBy;
}
