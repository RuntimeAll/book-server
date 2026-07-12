package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizFeedbackSheet;

/**
 * BizFeedbackSheet Mapper（PRD-004 课后反馈单）。
 *
 * <p>🔴 类级 {@code @InterceptorIgnore(tenantLine="true")} 直挂 —— biz_feedback_sheet 无
 * tenant_id，接口注解不随父接口继承，继承方法会被租户拦截器拼 {@code AND tenant_id} 报错，
 * 必须每个具体 mapper 直挂（同 BizCoursePlanMapper）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizFeedbackSheetMapper extends BizBaseMapper<BizFeedbackSheet> {
}
