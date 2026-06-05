package org.dromara.book.service;

import org.dromara.book.domain.bo.CreateExamPaperBo;
import org.dromara.book.domain.bo.PaperLazyTreeBo;
import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.bo.UpdateExamPaperBo;
import org.dromara.book.domain.vo.CreateExamPaperVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.PaperCategoryNodeVo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperListItemVo;

import java.util.List;

/**
 * 卷库 Service 接口（D 卡 V0.5 卷库视觉级还原）。
 *
 * <p>承担 2 个 misikt 风格端点：
 * <ul>
 *   <li>POST /teacher/exam/paper/lazyTree — 试卷分类树（97 节点 / 3 根）</li>
 *   <li>POST /teacher/exam/paper/page — 试卷分页列表（misikt PageHelper 完整结构）</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface IPaperLibraryService {

    /**
     * POST /teacher/exam/paper/lazyTree — 拉试卷分类树。
     *
     * <p>策略：一次性 SELECT * FROM biz_paper_category WHERE parent_id NOT LIKE '-%' + 内存建树。
     * 表只 97 行（+6 deprecated 软删行），一次拉无性能压力。
     *
     * <p>入参 bo.type / bo.version V0.5 BE 忽略（misikt 真站固定值，我们仅有"浙教新版"）。
     *
     * @param bo 入参 BO（type=2 + version=1010，V0.5 忽略）
     * @return 树（含 3 根：3001 公共试卷 / 3003 资料库 / 3004 专题卷库）
     */
    List<PaperCategoryNodeVo> lazyTree(PaperLazyTreeBo bo);

    /**
     * POST /teacher/exam/paper/page — 分页拉试卷列表。
     *
     * <p>过滤：status='1' / name LIKE %name% / subject_id LIKE 'subjectId%'，
     * 排序：sort DESC。响应 PageHelper 完整结构（misikt 真站对齐）。
     *
     * @param bo 分页 + 筛选入参（misikt 风格 pageIndex / subjectId / name）
     * @return misikt 风格分页 VO
     */
    MisiktPageVo<PaperListItemVo> page(PaperPageBo bo);

    /**
     * Q 卡 段① — 创建试卷（POST /teacher/exam/paper/create）。
     *
     * <p>业务流（@Transactional 整体回滚）：
     * <ol>
     *   <li>INSERT biz_paper（status='1' 发布，create_by=String.valueOf(currentUserId)，paper_type=1 手工，
     *       question_count = questionIds.size()，score=0，sort=0）</li>
     *   <li>INSERT biz_paper_section 默认 section（title="题目"，sort=1）</li>
     *   <li>批量 INSERT biz_paper_question（sort 按 questionIds 顺序 1/2/3...，score=0）</li>
     * </ol>
     *
     * <p>FE 拿 paperId 跳 /papers/source/{paperId} 卷详情 + 清空试题栏 LS。
     *
     * @param bo 创建入参（name + questionIds + paperCategoryId?）
     * @return 新建试卷 ID + 题目数
     */
    CreateExamPaperVo createExamPaper(CreateExamPaperBo bo);

    /**
     * PRD-A-005 T3 — 编辑试卷（POST /teacher/exam/paper/update）。
     *
     * <p>业务流（@Transactional 整体回滚）：
     * <ol>
     *   <li>校验 paperId 存在（不存在抛 ServiceException）</li>
     *   <li>删该 paperId 旧 biz_paper_question 全部行</li>
     *   <li>按 bo.questions 批量重插（section_id / question_id / sort / score）</li>
     *   <li>重算并更新 biz_paper.question_count（题数）+ score（各题 score 之和）；
     *       name / paperCategoryId 如传则一并更新</li>
     * </ol>
     *
     * <p>questions = 该卷编辑后的最终完整题集（按 sort 升序）。
     *
     * @param bo 编辑入参（paperId + name? + paperCategoryId? + questions[]）
     * @return 更新后的试卷详情 {@link PaperDetailVo}（复用 detail 查询）
     */
    PaperDetailVo updateExamPaper(UpdateExamPaperBo bo);

    /**
     * PRD-A-005 收尾（A-试卷删除）— 删除试卷（POST /teacher/exam/paper/delete）。
     *
     * <p>业务流（@Transactional 整体回滚 + TenantHelper.ignore(DataPermissionHelper.ignore(...))
     * 包裹，三表无 tenant_id 列规避多租户拦截器注入报错）：
     * <ol>
     *   <li>owner 校验：查该 paper 的 create_by，≠ 当前登录 userId 抛 ServiceException（公共卷/他人卷一律拒绝）</li>
     *   <li>级联物理删 biz_paper_question（该卷全部）</li>
     *   <li>级联物理删 biz_paper_section（该卷全部）</li>
     *   <li>物理删 biz_paper 本体</li>
     * </ol>
     *
     * @param paperId 试卷 ID
     */
    void deleteExamPaper(Long paperId);
}
