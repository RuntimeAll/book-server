package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizShelfNode;

/**
 * 书架·目录节点 Mapper（biz_shelf_node，PRD-002）。
 *
 * <p>🔴 {@code @InterceptorIgnore(tenantLine="true")} 必须直挂（biz_* 表无 tenant_id，注解不随父接口继承）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizShelfNodeMapper extends BizBaseMapper<BizShelfNode> {
}
