package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizTuitionFlow;

/**
 * BizTuitionFlow Mapper（PRD-015 课时课费流水）。
 *
 * <p>🔴 类级 {@code @InterceptorIgnore(tenantLine = "true")} 直挂本接口（同 BizTuitionAccountMapper）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizTuitionFlowMapper extends BizBaseMapper<BizTuitionFlow> {
}
