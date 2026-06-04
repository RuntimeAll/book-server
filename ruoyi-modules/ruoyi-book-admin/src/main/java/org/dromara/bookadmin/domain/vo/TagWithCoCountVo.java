package org.dromara.bookadmin.domain.vo;

import lombok.Data;

/**
 * 标签 + 与某知识点共现次数 VO（PRD-B-006 收尾增量 — 标签按知识点过滤端点出参）。
 *
 * <p>数据源：{@code biz_tag_knowledge}（V9 ETL 灌库的"tag ↔ knowledge 共现"反推表）
 * JOIN {@code biz_free_tag}（取 tag 文本名）。
 *
 * <p>查询语义：给定一个 {@code knowledgeId}（biz_subject.id，任意层级），按 {@code co_count}
 * 倒序返本知识点下"共现次数最多"的 top N 个标签 — FE 在 admin 列表页"标签筛选"弹层
 * 里选了"前置知识点"后用本端点反推"应该展示哪些候选标签"。
 *
 * <p>与 {@code biz_free_tag} 全量字典搜索（{@link
 * org.dromara.bookadmin.mapper.AdminFreeTagWriteMapper#selectListByKeyword}）的区别：
 * 那个返"全字典 + 全局 use_count"，不分知识点；本 VO 返"指定 knowledge 下共现 top"，
 * 有强语义约束。
 *
 * <p>字段：
 * <ul>
 *   <li>{@code tagId} — biz_free_tag.id（亦即 biz_tag_knowledge.tag_id）</li>
 *   <li>{@code tagName} — biz_free_tag.name（标签文本）</li>
 *   <li>{@code coCount} — biz_tag_knowledge.co_count（该 tag 与传入 knowledge 同挂题数）</li>
 * </ul>
 *
 * @author backend-dev (PRD-B-006 收尾增量)
 */
@Data
public class TagWithCoCountVo {

    /** biz_free_tag.id（亦 biz_tag_knowledge.tag_id）。 */
    private Long tagId;

    /** biz_free_tag.name — 标签文本名。 */
    private String tagName;

    /** biz_tag_knowledge.co_count — 该 tag 与 knowledge_id 共现题数。 */
    private Integer coCount;
}
