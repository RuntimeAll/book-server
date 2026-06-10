package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizVariantUpload;

/**
 * 举一反三母题图上传留痕 Mapper（biz_variant_upload，V904）。
 *
 * <p>继承 {@link BizBaseMapper}（类级 {@code @InterceptorIgnore(tenantLine="true")}，
 * biz_* 表无 tenant_id 列），上传留痕只需 insert，不需要自定义 SQL。
 *
 * @author backend-dev
 */
@Mapper
public interface BizVariantUploadMapper extends BizBaseMapper<BizVariantUpload> {
}
