package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizTuitionAccount;

/**
 * BizTuitionAccount Mapper（PRD-015 课时课费账户）。
 *
 * <p>🔴 类级 {@code @InterceptorIgnore(tenantLine = "true")} 直挂本接口 —— biz_* 表无 tenant_id，
 * 且接口注解不随父接口 BizBaseMapper 继承（interface 注解非 @Inherited），继承方法会被租户拦截器
 * 拼 {@code AND tenant_id} 报 Unknown column。必须每个具体 mapper 直挂。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizTuitionAccountMapper extends BizBaseMapper<BizTuitionAccount> {
}
