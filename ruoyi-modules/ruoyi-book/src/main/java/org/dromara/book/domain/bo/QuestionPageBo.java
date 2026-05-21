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
}
