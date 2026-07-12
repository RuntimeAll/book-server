package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizShelfItem;

/**
 * 书架·内容项 Mapper（biz_shelf_item，PRD-002）。
 *
 * <p>🔴 {@code @InterceptorIgnore(tenantLine="true")} 必须直挂（biz_* 表无 tenant_id，注解不随父接口继承）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizShelfItemMapper extends BizBaseMapper<BizShelfItem> {
}
