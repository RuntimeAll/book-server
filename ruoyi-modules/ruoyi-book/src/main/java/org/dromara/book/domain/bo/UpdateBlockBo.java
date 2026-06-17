package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * PRD-A-015 — 题目结构化编辑入参 BO（POST /teacher/question/update-block）。
 *
 * <p>🔴 C-100 B-converge：A-015 原名为 {@code UpdateQuestionBo} + 端点 {@code /teacher/question/update}，
 * 与 C-015「覆盖原行」({@link UpdateQuestionBo}=CreateQuestionBo+id) 撞名撞端点。维护者拍板「两套并存、A 改名」：
 * A 的「结构化网格块编辑」整体改名 {@code UpdateBlockBo} + {@code updateBlock()} + {@code POST /teacher/question/update-block}。
 *
 * <p>权威源 = {@code blockJson}（结构化网格块 JSON，§10.1 schema）。可选元数据
 * 字段（questionType/difficult/subjectId/stem/answer/analyze）仅在传了（非空）时同步更新
 * 对应 biz_question 列 / biz_text_content 行 —— stem 同步用于列表/搜索回落显示。
 *
 * <p>🔴 绝不从 body 接收 {@code createUser / createBy / status / id} —— 服务端强制
 * （归属 = 原题 owner，编辑前做 OWNER 校验；status 不变；id = questionId 指定改哪道题）。
 * 前端传了这些也忽略。
 *
 * @author backend-dev (PRD-A-015 · 改名 PRD-C-100 B-converge)
 */
@Data
public class UpdateBlockBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ===== 必填 =====

    /** 改哪道题 → biz_question.id（必填） */
    @NotNull(message = "questionId 不能为空")
    private Long questionId;

    /** 结构化内容（权威源，§10.1 schema）→ biz_question_block.block_json（必填） */
    @NotBlank(message = "blockJson 结构化内容不能为空")
    private String blockJson;

    // ===== 可选元数据（传了才更新对应 biz_question 列 / biz_text_content 行） =====

    /** 题型 1选择/4填空/5简答 → biz_question.question_type（传了才更新） */
    private Integer questionType;

    /** 难度 1-4 星 → biz_question.difficult（传了才更新） */
    private Integer difficult;

    /** 章节 / 知识点编码 → biz_question.subject_id（传了才更新） */
    private String subjectId;

    /** 题干长文本（供列表/搜索回落显示）→ 外置 biz_text_content content_type='S'（非空才同步） */
    private String stem;

    /** 答案长文本 → 外置 biz_text_content content_type='A'（非空才同步） */
    private String answer;

    /** 解析长文本 → 外置 biz_text_content content_type='E'（非空才同步） */
    private String analyze;
}
