package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizIngestJobItem;

/**
 * 批量录题作业拆出题暂存 Mapper（biz_ingest_job_item，V27，PRD-A-002 路B）。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizIngestJobItemMapper extends BizBaseMapper<BizIngestJobItem> {
}
