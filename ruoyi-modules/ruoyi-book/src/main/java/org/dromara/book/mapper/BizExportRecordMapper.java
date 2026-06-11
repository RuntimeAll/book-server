package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizExportRecord;

/**
 * 试卷导出任务记录 Mapper（biz_export_record，V20，PRD-A-014）。
 *
 * <p>继承 {@link BizBaseMapper}（类级 {@code @InterceptorIgnore(tenantLine="true")}，
 * biz_* 表无 tenant_id 列），CRUD + 分页走 MyBatis-Plus Wrapper，无自定义 SQL。
 *
 * @author backend-dev
 */
@Mapper
public interface BizExportRecordMapper extends BizBaseMapper<BizExportRecord> {
}
