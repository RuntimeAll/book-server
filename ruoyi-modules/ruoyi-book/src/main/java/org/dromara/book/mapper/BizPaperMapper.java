package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperSectionVo;
import org.dromara.book.domain.vo.PaperSourceQuestionVo;
import org.dromara.book.domain.vo.PaperSourceVo;

import java.util.List;

/**
 * 试卷主表 Mapper（biz_paper）。
 *
 * <p>本 mapper 不绑 BaseMapperPlus（无 entity 类）— 承担:
 * <ul>
 *   <li>V1 B-10: 卷头 + 卷下题（JOIN biz_paper_question + biz_question）</li>
 *   <li>D 卡 V0.5: 分页查询试卷列表（POST /teacher/exam/paper/page）</li>
 *   <li>E 卡段②: 试卷详情（POST /teacher/exam/paper/detail） — 卷头 + sections 分组 + 题列表</li>
 * </ul>
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

    /**
     * E 卡段② — 试卷详情卷头（POST /teacher/exam/paper/detail 第 1 步）。
     *
     * <p>查 biz_paper 一行：返完整卷头字段（paperId/paperName/subjectId/score/suggestTime/
     * questionCount/examYear/paperType）。
     *
     * <p>过滤 status='1' — 草稿 / 软删返 null。
     *
     * @param paperId 试卷 id
     * @return 卷头 VO（不含 sections，由 service 二次填充），或 null
     */
    PaperDetailVo selectPaperDetailHeader(@Param("paperId") Long paperId);

    /**
     * E 卡段② — 试卷的大题分组（POST /teacher/exam/paper/detail 第 2 步）。
     *
     * <p>查 biz_paper_section 全部行（paper_id=?），按 sort ASC（注意 sort 可能跳号 1/3/4，
     * 必须 ORDER BY 而不能假设连续）。返 VO 不含 questions（由 service 第 3 步分组填充）。
     *
     * @param paperId 试卷 id
     * @return sections 列表（可能空 list — paper 无 section 的极端情况）
     */
    List<PaperSectionVo> selectSectionsByPaperId(@Param("paperId") Long paperId);

    /**
     * E 卡段② — 试卷下所有题（POST /teacher/exam/paper/detail 第 3 步）。
     *
     * <p>JOIN biz_paper_question + biz_question，过滤 q.status='1'，按 pq.sort ASC。
     * 跟 {@link #selectQuestionsByPaperId} 的唯一区别：本方法返 {@link QuestionWithSectionId}
     * 内部类（携带 sectionId 字段），让 Service 端按 section_id 分组到对应 section。
     *
     * @param paperId 试卷 id
     * @return 题列表（含 sectionId）
     */
    List<QuestionWithSectionId> selectQuestionsByPaperIdWithSection(@Param("paperId") Long paperId);

    /**
     * 内部容器：{@link PaperSourceQuestionVo} + sectionId（仅供 Service 端按 section 分组，不向外暴露）。
     *
     * <p>用法跟 {@code BizQuestionFreeTagMapper.FreeTagWithQid} 同源 —— Service 拿到后
     * 复制成纯 {@link PaperSourceQuestionVo} 放进 {@link PaperSectionVo#questions}，
     * sectionId 字段不进响应 JSON。
     */
    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    class QuestionWithSectionId extends PaperSourceQuestionVo {
        private static final long serialVersionUID = 1L;
        /**
         * 大题 id（biz_paper_question.section_id），用于 Service 端按 section 分组
         */
        private Long sectionId;
    }

    /**
     * D 卡卷库视觉级还原 — 分页查询试卷列表（POST /teacher/exam/paper/page）。
     *
     * <p>走 MyBatis-Plus 分页插件（@Param(Constants.WRAPPER) 注入 ${ew.customSqlSegment}）。
     * 字段类型按 misikt 真响应口径 CAST：score / hgScore DECIMAL → Integer，
     * status CHAR(1) → Integer，create_by VARCHAR → Integer，create_time DATETIME 透传 Date。
     *
     * @param page    MyBatis-Plus 分页对象
     * @param wrapper Wrapper 注入 WHERE 条件（name LIKE / subject_id 前缀 / status / orderBy）
     * @return 分页结果（records 是 PaperListItemVo）
     */
    com.baomidou.mybatisplus.core.metadata.IPage<org.dromara.book.domain.vo.PaperListItemVo> selectPaperListPage(
        com.baomidou.mybatisplus.core.metadata.IPage<org.dromara.book.domain.vo.PaperListItemVo> page,
        @Param(com.baomidou.mybatisplus.core.toolkit.Constants.WRAPPER)
        com.baomidou.mybatisplus.core.conditions.Wrapper<org.dromara.book.domain.vo.PaperListItemVo> wrapper);
}
