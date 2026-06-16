package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizTeacherAiMemory;

/**
 * 老师全局 AI 记忆 Mapper（biz_teacher_ai_memory）。
 *
 * <p>继承 {@link BizBaseMapper} —— 表无 tenant_id 列，类级 {@code @InterceptorIgnore(tenantLine="true")}
 * 落在基类，命中所有继承方法（selectList/insert/updateById/deleteById），不再人肉 TenantHelper.ignore。
 *
 * <p>本 mapper 不需要自定义 SQL —— list/CRUD/toggle 全走 LambdaQueryWrapper + BaseMapperPlus 即可。
 *
 * @author PRD-C-100 B4
 */
@Mapper
public interface BizTeacherAiMemoryMapper extends BizBaseMapper<BizTeacherAiMemory> {
}
