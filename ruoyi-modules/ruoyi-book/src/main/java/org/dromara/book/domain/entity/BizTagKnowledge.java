package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 标签↔知识点 共现关联实体（biz_tag_knowledge）— PRD-B-006 收尾增量。
 *
 * <p>基于现有题目挂载 ({@code biz_question_free_tag} × {@code biz_question_knowledge}) 逆推：
 * 同一题被同时打上 tag X 和挂上 knowledge Y → 视为 X↔Y 共现一次。
 * 给 AI 编排 / 反向推荐用（"老师选了标签 → 推可能的知识点"）。
 *
 * <p>表设计点：
 * <ul>
 *   <li>复合主键 {@code (tag_id, knowledge_id)} — 一对组合一行</li>
 *   <li>无自增 id，MyBatis-Plus 用 {@code @TableId(type = INPUT)} 不适合双主键，
 *       本实体仅做查询 / 批量插入用，主键由 ETL 写入语义决定（不暴露给 service insert(entity)）</li>
 *   <li>{@code co_count} — 共现次数；{@code idx_knowledge_count} 索引按知识点 + 倒序共现支撑 "knowledgeId → topN 标签" 查询</li>
 * </ul>
 *
 * @author backend-dev (PRD-B-006)
 */
@Data
@TableName("biz_tag_knowledge")
public class BizTagKnowledge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * biz_free_tag.id
     */
    private Long tagId;

    /**
     * biz_subject.id（任意层级编码 / VARCHAR）
     */
    private String knowledgeId;

    /**
     * 该 (tag_id, knowledge_id) 在多少道题上同时出现（DISTINCT question_id 计数）
     */
    private Integer coCount;

    @TableField(update = "now()")
    private Date updateTime;
}
