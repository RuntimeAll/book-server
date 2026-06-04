package org.dromara.bookadmin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.bookadmin.domain.vo.TagWithCoCountVo;

import java.util.List;

/**
 * biz_tag_knowledge 共现反推表读 Mapper（PRD-B-006 收尾增量 — 标签按知识点过滤端点）。
 *
 * <p>数据来源：V9 ETL 灌库的 {@code biz_tag_knowledge}（tag_id, knowledge_id, co_count）—
 * 共 23635 行覆盖现有题目挂载的全部 tag ↔ knowledge 组合。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — biz_tag_knowledge +
 * biz_free_tag 业务表都无 tenant_id 列（B 卡 T7 教训沉淀，参见
 * {@link AdminFreeTagWriteMapper} 同款注解原因）。
 *
 * <p>不绑 BaseMapperPlus（无对应 entity 类，PRD-B-006 段定位 = 反推查询表，admin 不写它），
 * mapper.xml 落 {@code ruoyi-book-admin/src/main/resources/mapper/bookadmin/AdminTagKnowledgeMapper.xml}。
 *
 * @author backend-dev (PRD-B-006 收尾增量)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AdminTagKnowledgeMapper {

    /**
     * 按 knowledge_id 查与之共现的 tag 列表，按共现次数倒序取 top N（PRD-B-006 收尾增量）。
     *
     * <p>SQL 等效：
     * <pre>{@code
     * SELECT tk.tag_id AS tagId, ft.name AS tagName, tk.co_count AS coCount
     * FROM biz_tag_knowledge tk
     * JOIN biz_free_tag ft ON ft.id = tk.tag_id
     * WHERE tk.knowledge_id = #{knowledgeId}
     * ORDER BY tk.co_count DESC, tk.tag_id ASC
     * LIMIT #{topN}
     * }</pre>
     *
     * <p>索引利用：{@code biz_tag_knowledge.idx_knowledge_count(knowledge_id, co_count DESC)}
     * 命中 WHERE + ORDER BY 全列，无 filesort。
     *
     * <p>同 co_count 用 tag_id ASC 兜底稳定排序（避免分页 / 重复请求 order 不稳）。
     *
     * @param knowledgeId biz_subject.id（任意层级编码，varchar 字符串）
     * @param topN        返回上限（service 层兜底 50，clamp ≤ 200）
     * @return tag 候选列表（最多 topN 行，按 coCount 倒序）
     */
    List<TagWithCoCountVo> selectTopByKnowledge(@Param("knowledgeId") String knowledgeId,
                                                 @Param("topN") int topN);
}
