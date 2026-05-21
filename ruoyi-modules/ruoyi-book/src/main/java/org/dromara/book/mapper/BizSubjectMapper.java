package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 章节-知识点树 Mapper（biz_subject）。
 *
 * <p>用 RuoYi-Vue-Plus 自家 {@link BaseMapperPlus}（带 selectList / selectOne / 等扩展），
 * 跟 ruoyi-system 模块风格对齐。
 *
 * @author backend-dev
 */
@Mapper
public interface BizSubjectMapper extends BaseMapperPlus<BizSubject, BizSubject> {
}
