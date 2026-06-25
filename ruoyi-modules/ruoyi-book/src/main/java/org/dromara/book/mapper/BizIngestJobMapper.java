package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizIngestJob;

/**
 * 批量录题作业 Mapper（biz_ingest_job，V27，PRD-A-002 路B）。
 *
 * <p>继承 {@link BizBaseMapper}（biz_* 表无 tenant_id），并自带 {@code @InterceptorIgnore(tenantLine="true")}
 * （MyBatis-Plus 按 mapperClass.getAnnotation() 读注解、不从父接口继承，故此处显式补上）。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizIngestJobMapper extends BizBaseMapper<BizIngestJob> {
}
