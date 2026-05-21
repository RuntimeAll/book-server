package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 章节-知识点树 Mapper（biz_subject）。
 *
 * <p>biz_* 表跟租户解耦（misikt 业务无多租户场景），@InterceptorIgnore 关 MyBatis-Plus
 * TenantLineInnerInterceptor 自动注入的 tenant_id where（biz_subject 表无 tenant_id 字段）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizSubjectMapper extends BaseMapperPlus<BizSubject, BizSubject> {
}
