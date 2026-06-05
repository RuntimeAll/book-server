package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目知识点关联 VO（questionKnowledges / questionStdKnowledges 数组元素）。
 *
 * <p>来源 biz_question_knowledge LEFT JOIN biz_subject — 取 subject.name。
 * misikt JSON 字段命名严格对齐（参 A3-question-page.json）。
 *
 * @author backend-dev
 */
@Data
public class QuestionKnowledgeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * biz_question_knowledge.id（misikt 抓包此处常为 null — 我们落实表自增 ID）
     */
    private Long id;

    private Long questionId;

    /**
     * biz_subject.id（叶子或任意层级编码）
     */
    private String knowledgeId;

    /**
     * 知识点名（取自 biz_subject.name）
     */
    private String knowledgeName;

    /**
     * 关联记录创建时间（datetime → string，misikt 抓包恒 null；本工程返 null 兼容）
     */
    private String createTime;
}
