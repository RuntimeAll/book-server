package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.vo.QuestionPaperVo;

import java.util.List;

/**
 * 试卷-题目关联 Mapper（biz_paper_question）。
 *
 * <p>本 mapper 不绑 BaseMapperPlus（无 entity 类）— 只承担 B-6 端点的 JOIN 查询，
 * 走 mapper.xml 自定义 SQL。如后续需要 CRUD 再补 entity + BaseMapperPlus。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — biz_paper_question /
 * biz_paper 业务表无 tenant_id 列，否则脚手架数据隔离拦截器会拼 {@code AND tenant_id = ?}
 * 撞 SQLSyntaxErrorException（B 卡 T7 教训沉淀）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizPaperQuestionMapper {

    /**
     * GET /teacher/qd/papers/{id} 业务查询 — 这题在哪些卷。
     *
     * <p>JOIN biz_paper_question + biz_paper，按 biz_paper.create_time 倒序，
     * 仅返已发布卷（biz_paper.status='1'），过滤草稿/软删。
     *
     * @param questionId 题目 id
     * @return 该题出现在哪些卷里（可能为空 list）
     */
    List<QuestionPaperVo> selectPapersByQuestionId(@Param("questionId") Long questionId);
}
