package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 组卷草稿单个分组（按题型）。
 *
 * <p>对应 misikt /teacher/question/genExamData/ 响应里 {@code sections[]} 的元素 — 一个题型一节，
 * 含中文标题（"一、选择题" 等）+ 题型代码 + 该分组下题目列表。
 *
 * <p>字段命名跟 FE 契约（{@code title / questionType / questions}）严格对齐 — FE 工作台读这个结构
 * 渲染 section 头 + 题目排版。
 *
 * @author backend-dev
 */
@Data
public class ExamSectionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 中文 section 标题，如 "一、选择题" / "二、填空题" / "三、简答题"。
     */
    private String title;

    /**
     * 题型代码 — 1=选择 / 4=填空 / 5=简答（misikt 真实只 3 种）。
     */
    private Integer questionType;

    /**
     * 该 section 下题目列表（已回填 questionKnowledges）。
     */
    private List<QuestionItemVo> questions;
}
