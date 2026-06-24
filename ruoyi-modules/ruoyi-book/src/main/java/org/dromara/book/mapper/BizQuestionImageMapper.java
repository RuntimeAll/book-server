package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizQuestionImage;

/**
 * 题目-图片关联 Mapper（biz_question_image）—— PRD-C-204 B1 新建。
 * 无 tenant_id 列，继承 {@link BizBaseMapper} 关多租户拦截器。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionImageMapper extends BizBaseMapper<BizQuestionImage> {
}
