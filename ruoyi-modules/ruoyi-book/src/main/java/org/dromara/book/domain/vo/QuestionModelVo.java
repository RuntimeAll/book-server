package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目命中的解题模型（biz_question_model JOIN biz_solution_model）。
 *
 * <p>/teacher/question/select/{id} 详情回填 {@code models} 列表，主模型（is_primary=1）在前。
 * 高级属性页「解题模型」区只读展示。
 *
 * @author backend-dev
 */
@Data
public class QuestionModelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型 id（biz_solution_model.id，如 DZ11 / M001） */
    private String modelId;

    /** 模型名（如「大招11 双中点模型」） */
    private String name;

    /** 模型大类（线段 / 通用 / …） */
    private String category;

    /** 难度阶：1 基础阶 / 2 高阶 */
    private Integer difficultyTier;

    /** 考频：1 低频一次性 / 2 高频通用 */
    private Integer freqBand;

    /** 是否书金标：1 金标 / 0 反推补充 */
    private Integer isGold;

    /** 是否主模型：1 主 / 0 辅 */
    private Integer isPrimary;

    /** 题在该模型里的角色（母题 / 变式 / 应用） */
    private String role;

    /** 来源：AI / 人工 */
    private String source;
}
