package org.dromara.bookadmin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.bookadmin.domain.vo.TagWithCoCountVo;

import java.util.List;

/**
 * 按学科节点(任意 level 1-5)反推共现 top N 标签 — 实时算 Mapper(PRD-B-006 收尾·修正).
 *
 * <p>替代废弃的 {@code AdminTagKnowledgeMapper}(基于 V8/V9 预聚合表 biz_tag_knowledge)，
 * 改走实时三表 JOIN:
 * <pre>{@code
 *   biz_question_free_tag qft
 *     JOIN biz_question_knowledge qk ON qk.question_id = qft.question_id
 *     JOIN biz_free_tag ft ON ft.id = qft.tag_id
 *   WHERE qk.knowledge_id LIKE CONCAT(#{subjectId}, '%')
 * }</pre>
 *
 * <p>用 ID 前缀 LIKE 匹配 — biz_subject.id 是层级编码字符串(如 "3071001" → "3071001003" 是其子),
 * 任意 level 1-5 节点都可作 subjectId 传入, 自动聚合整棵子树下所有题目的标签共现.
 *
 * <p>实测性能(2026-06-04, dev 库): 七年级上册 (3071001) 6.5ms / level 2 节点 (3120004) 2.7ms,
 * 完全满足 admin 列表页"标签筛选弹层"实时下拉的延迟预算; 性能等价预聚合方案, 还多出"任意 level 子树"
 * 聚合能力 — 预聚合只能等值精确匹配 knowledge_id, 不能跨子树.
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — biz_question_free_tag /
 * biz_question_knowledge / biz_free_tag 三张业务表都无 tenant_id 列(B 卡 T7 教训沉淀,
 * 参见 {@link AdminFreeTagWriteMapper} 同款注解原因).
 *
 * <p>不绑 BaseMapperPlus(无对应 entity 类), mapper.xml 落
 * {@code ruoyi-book-admin/src/main/resources/mapper/bookadmin/AdminTagSubjectMapper.xml}.
 *
 * @author backend-dev (PRD-B-006 收尾·修正方案)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AdminTagSubjectMapper {

    /**
     * 按学科节点(任意 level) ID 前缀匹配子树, 实时聚合 top N 共现标签.
     *
     * <p>SQL 等效:
     * <pre>{@code
     * SELECT qft.tag_id AS tagId,
     *        ft.name    AS tagName,
     *        COUNT(DISTINCT qft.question_id) AS coCount
     *   FROM biz_question_free_tag qft
     *   JOIN biz_question_knowledge qk ON qk.question_id = qft.question_id
     *   JOIN biz_free_tag ft ON ft.id = qft.tag_id
     *  WHERE qk.knowledge_id LIKE CONCAT(#{subjectId}, '%')
     *  GROUP BY qft.tag_id, ft.name
     *  ORDER BY coCount DESC, qft.tag_id ASC
     *  LIMIT #{topN}
     * }</pre>
     *
     * <p>性能保障: 走 biz_question_knowledge.knowledge_id 前缀索引(等价 left-anchored LIKE
     * 命中 B-tree range scan, MySQL 8 优化器自动用索引), 实测 ≤ 10ms.
     *
     * <p>同 coCount 用 tag_id ASC 兜底稳定排序(避免分页/重复请求 order 不稳).
     *
     * @param subjectId biz_subject.id(任意 level 1-5, varchar 字符串) — ID 前缀,
     *                  传 level 1 学段就聚合整学段, 传 level 5 知识点就聚合该知识点等值匹配
     * @param topN      返回上限(service 层兜底 50, clamp ≤ 200)
     * @return tag 候选列表(最多 topN 行, 按 coCount 倒序)
     */
    List<TagWithCoCountVo> selectTopBySubject(@Param("subjectId") String subjectId,
                                              @Param("topN") int topN);
}
