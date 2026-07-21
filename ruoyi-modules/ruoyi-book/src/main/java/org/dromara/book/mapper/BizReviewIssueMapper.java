package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizReviewIssue;

/**
 * 录题问题登记表 Mapper（biz_review_issue，PRD-006）。
 *
 * <p>🔴 {@code @InterceptorIgnore(tenantLine="true")} 必须直挂（biz_* 表无 tenant_id）。
 *
 * @author backend-dev (PRD-006 批1)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizReviewIssueMapper extends BizBaseMapper<BizReviewIssue> {
}
