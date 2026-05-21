package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.vo.PaperSourceQuestionVo;
import org.dromara.book.domain.vo.PaperSourceVo;

import java.util.List;

/**
 * 试卷主表 Mapper（biz_paper）。
 *
 * <p>本 mapper 不绑 BaseMapperPlus（无 entity 类）— 承担 B-10 端点的两个查询：
 * 卷头信息 + 卷下题列表（JOIN biz_paper_question + biz_question）。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — biz_paper / biz_paper_question /
 * biz_question 业务表无 tenant_id 列，否则多租户拦截器拼 {@code AND tenant_id = ?} 撞
 * SQLSyntaxErrorException（B 卡 T7 教训沉淀）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizPaperMapper {

    /**
     * 卷头信息查询（B-10 第一步）。
     *
     * <p>只查已发布卷（status='1'），返 paperId/paperName/examYear。
     * 不存在或 status≠'1' 返 {@code null}。
     *
     * @param paperId 试卷 id
     * @return 卷头 VO（不含 questions 列表，由 service 二次填充），或 null
     */
    PaperSourceVo selectPaperHeaderById(@Param("paperId") Long paperId);

    /**
     * 卷下所有题列表（B-10 第二步，JOIN biz_paper_question + biz_question）。
     *
     * <p>过滤 q.status='1' 已发布题；按 pq.sort 升序。复用 BizQuestionMapper 的字段命名口径
     * （stem_img_url → stemImg / UNIX_TIMESTAMP*1000 → createTime ms timestamp 等）。
     *
     * @param paperId 试卷 id
     * @return 卷下所有已发布题（可能为空 list）
     */
    List<PaperSourceQuestionVo> selectQuestionsByPaperId(@Param("paperId") Long paperId);
}
