package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.vo.FreeTagVo;

import java.util.Collection;
import java.util.List;

/**
 * 题目-freeTag 关联 Mapper（biz_question_free_tag JOIN biz_free_tag）。
 *
 * <p>X 卡段② / freeTag 字典化：用于按 question_id 批量回填结构化 freeTags 数组。
 *
 * <p>不继承 {@code BaseMapperPlus<Entity>} — 本 Mapper 只读不增删改，
 * 不为字典查询造 BizFreeTag / BizQuestionFreeTag entity（按主线段②约束）。
 *
 * <p>biz_question_free_tag 无 tenant_id 字段，关 MyBatis-Plus 多租户拦截器自动注入。
 *
 * @author backend-dev (X 卡段②)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionFreeTagMapper {

    /**
     * 按题目 ID 集合批量查关联 freeTags（含 biz_free_tag 关联取 name）。
     *
     * <p>SQL 排序：question_id ASC, position ASC（同题保留 position 顺序）。
     *
     * <p>返回的 VO 自带 questionId 字段是不可能的（VO 不含 questionId）—
     * 因此本 Mapper 走专用查询方法 {@link #selectGroupedByQuestionIds}，
     * 让 Service 端按 question_id 分组。
     *
     * @param questionIds 题目 ID 集合（不可空）
     * @return 关联 VO 列表（含临时透出的 questionId 列，由内层包装类型 {@link FreeTagWithQid} 承载）
     */
    List<FreeTagWithQid> selectGroupedByQuestionIds(@Param("questionIds") Collection<Long> questionIds);

    /**
     * 内部容器：FreeTagVo + questionId（仅供 Service 端分组，不向外暴露）。
     */
    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    class FreeTagWithQid extends FreeTagVo {
        private static final long serialVersionUID = 1L;
        /**
         * 题目 ID（biz_question_free_tag.question_id），用于 Service 端按题分组
         */
        private Long questionId;
    }
}
