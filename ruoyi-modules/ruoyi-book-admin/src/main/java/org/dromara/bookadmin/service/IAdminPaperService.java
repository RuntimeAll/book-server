package org.dromara.bookadmin.service;

import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.bookadmin.domain.bo.AdminPaperEditBo;
import org.dromara.bookadmin.domain.bo.AdminPaperPageBo;

/**
 * admin 卷库 Service 接口（PRD-B-006 round-2 — G3 修复）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本接口物理落 {@code ruoyi-book-admin} 模块，
 * <strong>禁注入</strong> {@code org.dromara.book.service.IPaperLibraryService}（教师端 Service）；
 * 实现内部直接走 {@code BizPaperMapper} 自查，admin 与 teacher 改方法互不波及。
 *
 * <p>端点路径前缀 {@code /admin/paper/...}，命中 RuoYi 标准 {@code R<T>} envelope，
 * <strong>不</strong>走 {@code MisiktEnvelopeAdvice}（只 scope {@code /teacher/} 前缀）；
 * book-admin (plus-ui) axios 拦截器天然走 RuoYi envelope，免转换。
 *
 * @author backend-dev (PRD-B-006 round-2)
 */
public interface IAdminPaperService {

    /**
     * 分页查询试卷列表（admin 通道）。
     *
     * <p>与教师端 {@code IPaperLibraryService#page} 当前等效，但独立维护：
     * admin 放开 status 过滤（可看草稿 '0' / 软删 '2'），教师端固定 status='1'。
     *
     * @param bo 分页 + 筛选入参（pageIndex / pageSize / name / subjectId / status）
     * @return misikt 风格分页 VO（list + total）
     */
    MisiktPageVo<BizPaper> adminPage(AdminPaperPageBo bo);

    /**
     * 试卷详情查询（编辑回填用）。
     *
     * <p>admin 通道不限 status（教师端只返 status='1'），让 admin 能查草稿。
     *
     * @param id 试卷 id
     * @return 试卷实体，未找到时返 {@code null}
     */
    BizPaper adminSelectById(Long id);

    /**
     * 试卷新建 / 编辑统一入口。
     *
     * <p>分支：
     * <ul>
     *   <li>{@code bo.id == null} → 新建：INSERT biz_paper (status='0' 草稿，paper_type=1 手工)</li>
     *   <li>{@code bo.id != null} → 编辑：UPDATE biz_paper（status 不动）</li>
     * </ul>
     *
     * <p>事务：{@code @Transactional(rollbackFor = Exception.class)}。
     *
     * @param bo 入参（id 可空 = 新建 / 非空 = 编辑）
     * @return 试卷 id（新建为新自增 / 编辑为原 id）
     */
    Long adminEdit(AdminPaperEditBo bo);

    /**
     * 试卷软删（状态 0/1 → 2）。
     *
     * <p>业务规则：
     * <ol>
     *   <li>试卷不存在 → 抛 {@code ServiceException("试卷不存在: id")}</li>
     *   <li>已软删 → 抛 {@code ServiceException("试卷已软删，不能重复删除")}</li>
     *   <li>未引用 → {@code UPDATE biz_paper SET status='2', update_time=NOW() WHERE id=?}</li>
     * </ol>
     *
     * <p>事务：{@code @Transactional(rollbackFor = Exception.class)}。
     *
     * @param id 试卷 id
     */
    void adminSoftDelete(Long id);
}
