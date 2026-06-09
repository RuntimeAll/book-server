package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizSubject;

/**
 * 章节-知识点树 Mapper（biz_subject）。
 *
 * <p>biz_* 表跟租户解耦（misikt 业务无多租户场景），@InterceptorIgnore 关 MyBatis-Plus
 * TenantLineInnerInterceptor 自动注入的 tenant_id where（biz_subject 表无 tenant_id 字段）。
 *
 * @author backend-dev
 */
@Mapper
public interface BizSubjectMapper extends BizBaseMapper<BizSubject> {
}
