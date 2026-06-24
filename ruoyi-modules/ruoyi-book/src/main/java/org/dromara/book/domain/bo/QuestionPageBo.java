package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * /teacher/question/page 入参 BO。
 *
 * <p>字段命名严格对齐 misikt 真实抓包（A3-question-page.json）：
 * <ul>
 *   <li>{@code pageIndex} — 不是 {@code pageNum}</li>
 *   <li>{@code keyWord} — 关键字筛选；V0.1 LIKE 实现（ngram fulltext 未配 my.cnf 走 LIKE 兜底）</li>
 *   <li>{@code difficult} — 不是 {@code difficulty}（1-4 星）</li>
 *   <li>{@code notTaskQuestion} / {@code notUsedQuestion} — 0=不限 / 1=过滤</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
public class QuestionPageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 页码（不是 pageNum！— misikt 风格）
     */
    private Integer pageIndex;

    /**
     * 每页数量
     */
    private Integer pageSize;

    /**
     * 章节-知识点编码筛选（biz_subject.id 任意层级；空或 "0" 表示不过滤）
     */
    private String subjectId;

    /**
     * 题型 1=选择 / 4=填空 / 5=简答（其他不存在；空表示不过滤）
     */
    private Integer questionType;

    /**
     * 难度 1-4 星（空表示不过滤）
     */
    private Integer difficult;

    /**
     * 关键字（题干 LIKE %xxx%；V0.1 不走 fulltext）
     */
    private String keyWord;

    /**
     * 过滤已被作业引用的题：0=不限 / 1=只看未被作业引用（V0.1 biz_task 暂无数据，实际等同 0）
     */
    private Integer notTaskQuestion;

    /**
     * 过滤已被试卷引用的题：0=不限 / 1=只看未被试卷引用
     */
    private Integer notUsedQuestion;

    /**
     * PRD-C-009「我的题库」：true=只看当前登录老师自己的题（create_user=自己，含举一反三跑出 + 上传）。
     * 空/false=不过滤（沿用原「题库」全量语义）。owner 由后端 LoginHelper 定，前端只传开关。
     */
    private Boolean mine;

    /**
     * 按出处试卷筛选（biz_question.exam_paper_id）。非 null 才过滤；null=不限。
     */
    private Long examPaperId;

    /**
     * 按打标态筛选（biz_question.label_status）：0=未标 / 1=AI已标 / 2=已审核。非 null 才过滤；null=不限。
     */
    private Integer labelStatus;

    /**
     * 按题型筛选（PRD-C-204，biz_question_pattern.id）：非 null 才 JOIN biz_question_pattern_rel
     * 只返回该题型的题；null=不过滤（行为不变）。
     */
    private Long patternId;
}
