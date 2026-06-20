package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizQuestionAi;

/**
 * AI 派生表 Mapper（biz_question_ai）—— PRD-C-014 B1 新建。
 *
 * <p>录题事务内写一行 AI 派生 DNA（{@link IQuestionService#create}）。继承 {@link BizBaseMapper}
 * 复用 insert；biz_question_ai 无 tenant_id 列，{@link BizBaseMapper} 类级
 * {@code @InterceptorIgnore(tenantLine="true")} 已关多租户拦截器（本接口再标一次显式声明）。
 *
 * @author backend-dev (PRD-C-014 B1)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionAiMapper extends BizBaseMapper<BizQuestionAi> {
}
