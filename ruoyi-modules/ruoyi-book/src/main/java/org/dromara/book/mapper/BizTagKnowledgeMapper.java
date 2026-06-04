package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizTagKnowledge;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 标签↔知识点 共现关联 Mapper（biz_tag_knowledge）— PRD-B-006 收尾增量。
 *
 * <p>无 tenant_id 列；关 MP 多租户拦截器自动注入。
 *
 * <p>本表 V9 ETL 一次性灌入，运行期暂只读（给 AI 编排 / 反向推荐用）；
 * 复合主键 (tag_id, knowledge_id) — BaseMapperPlus 的 selectById 不适用，
 * 走 selectList + QueryWrapper 即可。
 *
 * @author backend-dev (PRD-B-006)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizTagKnowledgeMapper extends BaseMapperPlus<BizTagKnowledge, BizTagKnowledge> {
}
