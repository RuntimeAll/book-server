package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.entity.BizQuestionKnowledge;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.Collection;
import java.util.List;

/**
 * 题目-知识点 Mapper（biz_question_knowledge）。
 *
 * <p>用于按 question_id 批量回填 questionKnowledges / questionStdKnowledges。
 * biz_question_knowledge 表无 tenant_id 字段，关 MyBatis-Plus 多租户拦截器自动注入。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionKnowledgeMapper extends BaseMapperPlus<BizQuestionKnowledge, BizQuestionKnowledge> {

    /**
     * 按题目 ID 集合 + source 批量查关联知识点（含 biz_subject 关联取 name/img/video）。
     *
     * @param questionIds 题目 ID 集合（不可空）
     * @param source      'U' 或 'S'
     * @return 关联 VO 列表（含 questionId 用于 service 端分组）
     */
    List<QuestionKnowledgeVo> selectByQuestionIdsAndSource(@Param("questionIds") Collection<Long> questionIds,
                                                           @Param("source") String source);
}
