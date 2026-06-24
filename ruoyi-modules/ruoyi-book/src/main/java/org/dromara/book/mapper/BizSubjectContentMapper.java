package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizSubjectContent;

/**
 * 知识点内容块 Mapper（biz_subject_content）—— PRD-C-204 B1 新建。
 * 无 tenant_id 列，继承 {@link BizBaseMapper} 关多租户拦截器。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizSubjectContentMapper extends BizBaseMapper<BizSubjectContent> {
}
