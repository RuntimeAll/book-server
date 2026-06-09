package org.dromara.book.mapper;

import org.dromara.book.domain.entity.BizQuestionAnnotation;

/**
 * 题目标注 Mapper（PRD-B-012 标注维度地基）。
 *
 * <p>继承 {@link BizBaseMapper} 统一关 MP 多租户拦截器（biz_question_annotation 无 tenant_id 列）。
 *
 * @author backend-dev
 */
public interface BizQuestionAnnotationMapper extends BizBaseMapper<BizQuestionAnnotation> {
}
