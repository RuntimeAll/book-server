package org.dromara.bookadmin.domain.vo;

import lombok.Data;

/**
 * 标签 + 与某学科节点子树共现题数 VO(PRD-B-006 收尾·修正方案 — 标签按学科节点过滤端点出参).
 *
 * <p>2026-06-04 用户拍板推翻 V8/V9 预聚合方案(biz_tag_knowledge 已 V10 DROP), 改实时算.
 * VO 字段保持不变, 仅语义/数据源说明同步.
 *
 * <p>数据源(实时算): {@code biz_question_free_tag JOIN biz_question_knowledge JOIN biz_free_tag},
 * 用 {@code knowledge_id LIKE CONCAT(subjectId, '%')} 前缀匹配子树, 实测 dev 库 ≤ 10ms.
 *
 * <p>查询语义: 给定一个 {@code subjectId}(biz_subject.id, 任意 level 1-5), 按 coCount 倒序返
 * 本节点子树下"共现题数最多"的 top N 个标签 — FE 在 admin 列表页"标签筛选"弹层里选了"前置学科节点"
 * 后用本端点反推"应该展示哪些候选标签".
 *
 * <p>与 {@code biz_free_tag} 全量字典搜索({@link
 * org.dromara.bookadmin.mapper.AdminFreeTagWriteMapper#selectListByKeyword})的区别:
 * 那个返"全字典 + 全局 use_count", 不分节点; 本 VO 返"指定 subject 子树下共现 top",
 * 有强语义约束 + 实时反映最新挂载.
 *
 * <p>字段:
 * <ul>
 *   <li>{@code tagId} — biz_free_tag.id</li>
 *   <li>{@code tagName} — biz_free_tag.name(标签文本)</li>
 *   <li>{@code coCount} — COUNT(DISTINCT question_id), 该 tag 与传入 subject 子树共现题数</li>
 * </ul>
 *
 * @author backend-dev (PRD-B-006 收尾·修正方案)
 */
@Data
public class TagWithCoCountVo {

    /** biz_free_tag.id. */
    private Long tagId;

    /** biz_free_tag.name — 标签文本名. */
    private String tagName;

    /** COUNT(DISTINCT qft.question_id) — 该 tag 与 subjectId 子树共现题数. */
    private Integer coCount;
}
