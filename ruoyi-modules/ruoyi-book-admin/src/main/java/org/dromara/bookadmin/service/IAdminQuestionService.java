package org.dromara.bookadmin.service;

import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.SubjectNodeVo;

import java.util.List;

/**
 * admin 题目 Service 接口（H1 卡 V-6 — 写操作统一事务入口）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本接口物理落 {@code ruoyi-book-admin} 模块，
 * 方法内部走 {@code BizQuestionMapper} 自查，<strong>禁调</strong>
 * {@code org.dromara.book.service.IQuestionService}（教师端 Service）的任何方法。
 * admin 与 teacher 改方法互不波及。
 *
 * <p>当前接口（V-5 重构 + 波 2a + 波 2b）：
 * <ul>
 *   <li>{@link #adminPage(QuestionPageBo)} — admin 分页</li>
 *   <li>{@link #adminLazyTree(SubjectLazyTreeBo)} — admin 章节-知识点树懒加载</li>
 *   <li>{@link #adminSelectById(Long)} — V-5 详情查询</li>
 *   <li>{@link #adminSoftDelete(Long)} — V-2 软删 + biz_paper_question 引用校验</li>
 *   <li>{@link #adminPublish(Long)} — V-3 status 0→1 发布</li>
 *   <li>{@link #adminEdit} — V-1 + V-6 新建/编辑统一事务入口（含 U 轨知识点全量替换 + 标签全量替换 + 字典自动建）</li>
 * </ul>
 *
 * <p>下波（V-4）补：
 * <ul>
 *   <li>{@code adminUploadFile(MultipartFile file, String type)} — V-4 图上传 + image_asset 记录</li>
 * </ul>
 *
 * <ul>
 *   <li>{@code adminEdit(AdminQuestionEditBo bo)} — V-1 新建 / 编辑统一端点</li>
 *   <li>{@code adminUploadFile(MultipartFile file, String type)} — V-4 图上传 + image_asset 记录</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface IAdminQuestionService {

    /**
     * 分页查询题目（admin 通道，方法内部走 Mapper 自查，不调教师端 service）。
     *
     * @param bo 分页 + 筛选入参（misikt 风格 pageIndex / keyWord / difficult / 等）
     * @return misikt 风格分页 VO
     */
    MisiktPageVo<QuestionItemVo> adminPage(QuestionPageBo bo);

    /**
     * 章节-知识点树懒加载（admin 通道，方法内部走 {@code BizSubjectMapper} 自查，
     * <strong>不</strong>调教师端 {@code ISubjectService.lazyTree}）。
     *
     * <p>V0.1 简化（与教师端等效）：一次性 SELECT * FROM biz_subject + 内存建树，
     * 忽略 {@code bo.parentId} 永远返整树（misikt 抓包真实行为）。
     *
     * <p>BUG-1 数据修复保留：DB 仍有少量 "节点 XXXX" 占位名（W-6 已大幅清洗，
     * 但兜底逻辑保留以防新增数据漏网），admin 端按 level 兜底前缀。
     *
     * @param bo 懒加载入参（parentId 当前忽略）
     * @return 嵌套树（学段 → 学科 → 教材 → 章节 → 知识点）
     */
    List<SubjectNodeVo> adminLazyTree(SubjectLazyTreeBo bo);

    /**
     * 题目详情查询（V-5 — 编辑回填用）。
     *
     * <p>与教师端 {@code QuestionServiceImpl#selectById} 当前等效（同 mapper SQL + 同回填策略），
     * 但独立维护 — admin 后续可解除 status='1' 限制 / 加创建人过滤等。
     *
     * <p>回填：
     * <ul>
     *   <li>{@code questionKnowledges}（source='U'，用户标注知识点）</li>
     *   <li>{@code questionStdKnowledges}（source='S'，标准库知识点）</li>
     *   <li>{@code freeTags}（biz_question_free_tag JOIN biz_free_tag）</li>
     * </ul>
     *
     * <p>鉴权：Controller 层 {@code @SaCheckPermission("admin:question:list")}（编辑回填属 list 范围；
     * 后续 V-1 edit 端点才挂 admin:question:edit 写权限）。
     *
     * @param id 题目 ID
     * @return 详情 VO，未找到时返 {@code null}
     */
    QuestionDetailVo adminSelectById(Long id);

    /**
     * 题目软删（V-2 — 状态 0/1 → 2，含引用校验）。
     *
     * <p>业务规则：
     * <ol>
     *   <li>查 biz_paper_question 引用次数 N</li>
     *   <li>N &gt; 0 → 抛 {@code ServiceException("该题被 N 张试卷引用，无法删除")}</li>
     *   <li>N == 0 → {@code UPDATE biz_question SET status='2', update_time=NOW() WHERE id=?}</li>
     * </ol>
     *
     * <p>软删<strong>不动</strong> biz_question_knowledge / biz_question_free_tag —
     * 历史试卷里仍能渲染知识点 tag（PRD §3.2）。
     *
     * <p>事务：{@code @Transactional(rollbackFor = Exception.class)}（虽只单表 UPDATE，
     * 但未来扩展可能加联动写，保留事务边界）。
     *
     * @param id 题目 ID
     */
    void adminSoftDelete(Long id);

    /**
     * 题目发布（V-3 — 状态机 0 → 1）。
     *
     * <p>SQL：{@code UPDATE biz_question SET status='1', update_time=NOW() WHERE id=? AND status='0'}。
     *
     * <p>影响行数 0 → 抛 {@code ServiceException("状态非法（当前不是草稿，不能发布）")}。
     *
     * <p>不允许反向：'1' → '0' / '2' → 任何（PRD §3.8）。
     *
     * @param id 题目 ID
     */
    void adminPublish(Long id);

    /**
     * 题目新建 / 编辑统一入口（V-1 + V-6 — H1 卡段② BE 波 2b）。
     *
     * <p>分支：
     * <ul>
     *   <li>{@code bo.id == null} → 新建：INSERT biz_question (status='0' 草稿)
     *       + INSERT biz_question_knowledge (source='U')
     *       + INSERT biz_question_free_tag + biz_free_tag 字典（自动建）</li>
     *   <li>{@code bo.id != null} → 编辑：UPDATE biz_question (status 不动，要发布走 publish)
     *       + 全量替换 biz_question_knowledge (source='U')
     *       + 全量替换 biz_question_free_tag</li>
     * </ul>
     *
     * <p>同一事务边界（{@code @Transactional(rollbackFor = Exception.class)}）：
     * 任意 step 抛 {@code ServiceException} 整体回滚。
     *
     * <p>校验（PRD §3.1 + §6 R7）：
     * <ul>
     *   <li>questionType ∈ {1,2,3,4,5} / difficult ∈ {1..4} / subjectId 存在</li>
     *   <li>选择题 (type=1)：optionsJson ≥ 2 + correctAnswer ∈ keys</li>
     *   <li>questionKnowledges.size ≥ 1，每个 knowledgeId 在 biz_subject 存在</li>
     *   <li>stemText 与 stemImgUrl 至少有一个非空（PRD §6 R7）</li>
     * </ul>
     *
     * <p>knowledge source 强制 'U'：防 FE 误传 'S' 污染标准库知识点（PRD §3.5）。
     *
     * @param bo 入参（id 可空 = 新建 / 非空 = 编辑）
     * @return 题目 ID（新建为新自增 / 编辑为原 id）
     */
    Long adminEdit(org.dromara.bookadmin.domain.bo.AdminQuestionEditBo bo);

    // 下波 V-4 (fileUpload) 在此补方法

}
