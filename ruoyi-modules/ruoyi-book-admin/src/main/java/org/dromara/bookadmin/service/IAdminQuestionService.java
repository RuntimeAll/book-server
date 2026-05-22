package org.dromara.bookadmin.service;

import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.vo.MisiktPageVo;
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
 * <p>当前接口（V-5 重构）：
 * <ul>
 *   <li>{@link #adminPage(QuestionPageBo)} — admin 分页（独立维护，未来可加 status='0' 草稿过滤 / 创建人过滤 等）</li>
 * </ul>
 *
 * <p>下波（V-1/V-2/V-3/V-4）补：
 * <ul>
 *   <li>{@code edit(AdminQuestionEditBo bo)} — V-1 新建 / 编辑统一端点</li>
 *   <li>{@code softDelete(Long id)} — V-2 软删 + biz_paper_question 引用校验</li>
 *   <li>{@code publish(Long id)} — V-3 status 0→1 发布</li>
 *   <li>{@code uploadFile(MultipartFile file, String type)} — V-4 图上传 + image_asset 记录</li>
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

    // 下波 V-1~V-4 在此补方法

}
