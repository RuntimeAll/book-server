package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizShelfBook;

/**
 * 书架·书 Mapper（biz_shelf_book，PRD-002）。
 *
 * <p>🔴 {@code @InterceptorIgnore(tenantLine="true")} 必须直挂本接口（biz_* 表无 tenant_id，
 * 接口注解不随父接口继承），否则租户拦截器拼 AND tenant_id 报 Unknown column。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizShelfBookMapper extends BizBaseMapper<BizShelfBook> {
}
