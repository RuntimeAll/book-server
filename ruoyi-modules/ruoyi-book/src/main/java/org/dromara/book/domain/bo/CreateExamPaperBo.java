package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Q 卡 段① — 创建试卷入参 BO（POST /teacher/exam/paper/create）。
 *
 * <p>来源：FE 试题栏 → 工作台 /question/compose → 用户输入试卷名 + LS 内题 ID 列表。
 *
 * <p>策略：BE 自动建一个默认 section（title="题目" sort=1），所有题挂下面（biz_paper_question.section_id NOT NULL 约束）。
 *
 * @author backend-dev
 */
@Data
public class CreateExamPaperBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 试卷名称 — FE 默认"未命名草稿"，1-200 字符 */
    @NotBlank(message = "试卷名称不能为空")
    @Size(min = 1, max = 200, message = "试卷名称长度需 1-200 字符")
    private String name;

    /** 题目 ID 列表 — 顺序即试卷内题目顺序（试题栏 LS 顺序），至少 1 题 */
    @NotEmpty(message = "题目列表不能为空")
    private List<Long> questionIds;

    /** 试卷分类 ID — 可选，默认 null（卷库目录树根级别） */
    private String paperCategoryId;
}
