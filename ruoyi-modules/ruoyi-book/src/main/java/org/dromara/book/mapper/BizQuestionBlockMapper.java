package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizQuestionBlock;

/**
 * 题目结构化网格块存储 Mapper（biz_question_block，V23，PRD-A-015）。
 *
 * <p>继承 {@link BizBaseMapper}（类级 {@code @InterceptorIgnore(tenantLine="true")}，
 * biz_* 表无 tenant_id 列），CRUD 走 MyBatis-Plus 内置，无自定义 SQL。
 *
 * <p>主键 = question_id（一题一份），upsert 走 service 端 selectById 判存在 → update/insert。
 *
 * @author backend-dev (PRD-A-015)
 */
@Mapper
public interface BizQuestionBlockMapper extends BizBaseMapper<BizQuestionBlock> {
}
