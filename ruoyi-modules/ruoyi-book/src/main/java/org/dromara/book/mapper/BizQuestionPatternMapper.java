package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizQuestionPattern;

/**
 * 题型目录 Mapper（biz_question_pattern）—— PRD-C-204 新建。
 * 无 tenant_id 列，继承 {@link BizBaseMapper} 关多租户拦截器。
 *
 * @author backend-dev (PRD-C-204)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionPatternMapper extends BizBaseMapper<BizQuestionPattern> {
}
