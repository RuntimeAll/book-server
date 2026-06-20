package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizDnaEditLog;

/**
 * DNA 编辑经验层留痕 Mapper（biz_dna_edit_log）。
 *
 * <p>继承 {@link BizBaseMapper} —— 表无 tenant_id 列，类级 {@code @InterceptorIgnore(tenantLine="true")}
 * 落在基类，命中所有继承方法（selectList/insert），不再人肉 TenantHelper.ignore。
 *
 * <p>本 mapper 不需要自定义 SQL —— 只写（insert）+ 可选 list（LambdaQueryWrapper）即可。
 *
 * @author PRD-C-100 B4
 */
@Mapper
public interface BizDnaEditLogMapper extends BizBaseMapper<BizDnaEditLog> {
}
