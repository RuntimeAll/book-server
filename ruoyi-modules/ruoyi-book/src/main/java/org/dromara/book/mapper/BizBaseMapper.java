package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 所有 biz_* 表 mapper 的统一基类.
 * <p>
 * biz_* 表均无 tenant_id 列, 类级 @InterceptorIgnore(tenantLine="true")
 * 让 namespace 落本接口, 命中所有继承方法 (selectById/insert/updateById/deleteById/delete-by-wrapper),
 * 业务代码不再人肉 TenantHelper.ignore 包裹.
 * <p>
 * 继承 {@link BaseMapperPlus}（RuoYi 自定义扩展 mapper，提供 selectVoById/insertBatch 等便利方法），
 * 因此原本继承 {@code BaseMapperPlus<X, X>} 的 BizXxxMapper 切到 {@code BizBaseMapper<X>} 无功能损失.
 * 这里固定 entity 与 vo 同型（T, T）——本项目所有 biz mapper 都是 {@code BaseMapperPlus<X, X>} 范式.
 * <p>
 * 未来某张 biz_* 表加 tenant_id 列, 对应 Mapper 改为直接 extends BaseMapper / BaseMapperPlus 不走本类即可.
 *
 * @param <T> entity 泛型
 */
@InterceptorIgnore(tenantLine = "true")
public interface BizBaseMapper<T> extends BaseMapperPlus<T, T> {
}
