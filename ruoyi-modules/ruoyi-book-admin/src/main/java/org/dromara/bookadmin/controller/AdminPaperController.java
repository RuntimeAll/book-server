package org.dromara.bookadmin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.bookadmin.domain.bo.AdminPaperEditBo;
import org.dromara.bookadmin.domain.bo.AdminPaperPageBo;
import org.dromara.bookadmin.service.IAdminPaperService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * book-admin 后台卷库管理 Controller（PRD-B-006 round-2 — G3 修复）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本 Controller 物理落 {@code ruoyi-book-admin}
 * 模块，<strong>禁注入</strong> {@code org.dromara.book.service.IPaperLibraryService}
 * （教师端 Service）；必须注入 admin 自有 {@link IAdminPaperService}，
 * 方法内部走 Mapper 自查。admin 与 teacher 改方法互不波及。
 *
 * <p>路径前缀 {@code /admin/paper/...}，命中 RuoYi 标准 {@code R<T>} envelope，
 * <strong>不</strong>走 {@code MisiktEnvelopeAdvice}（只 scope {@code /teacher/} 前缀）；
 * book-admin (plus-ui) axios 拦截器天然走 RuoYi envelope，免转换。
 *
 * <p>FE API 契约（codeplace-B/book-admin/src/api/admin/paper/index.ts）：
 * <ul>
 *   <li>POST /admin/paper/page — 卷库分页</li>
 *   <li>POST /admin/paper/select/{id} — 卷库详情</li>
 *   <li>POST /admin/paper/edit — 新建 / 编辑统一端点</li>
 *   <li>POST /admin/paper/delete/{id} — 软删</li>
 * </ul>
 *
 * <p>鉴权：每个方法独立挂 {@code @SaCheckPermission("admin:paper:xxx")} 注解，
 * 权限标识与 sys_menu 表维护对齐（list 权限 = 读端点；edit 权限 = 写端点）。
 *
 * @author backend-dev (PRD-B-006 round-2)
 */
@RestController
@RequestMapping("/admin/paper")
@RequiredArgsConstructor
public class AdminPaperController {

    private final IAdminPaperService adminPaperService;

    /**
     * POST /admin/paper/page — 试卷分页（admin 通道）。
     *
     * <p>与教师端 {@code /teacher/exam/paper/page} 差异：admin 不限 status，可看草稿 / 软删。
     *
     * <p>响应 envelope：RuoYi 标准 {@code {code:200, msg:"操作成功",
     * data:{list, total, pageNum, pageSize, ...}}}。
     */
    @SaCheckPermission("admin:paper:list")
    @PostMapping("/page")
    public R<MisiktPageVo<BizPaper>> page(@RequestBody(required = false) AdminPaperPageBo bo) {
        if (bo == null) {
            bo = new AdminPaperPageBo();
        }
        return R.ok(adminPaperService.adminPage(bo));
    }

    /**
     * POST /admin/paper/select/{id} — 试卷详情（编辑回填用）。
     *
     * <p>admin 通道不限 status（教师端只返 status='1'），让 admin 能查草稿。
     *
     * <p>响应 envelope：RuoYi 标准 {@code R<BizPaper>}。
     *
     * <p>鉴权：{@code admin:paper:list}（详情属 list 权限范围）。
     */
    @SaCheckPermission("admin:paper:list")
    @PostMapping("/select/{id}")
    public R<BizPaper> select(@PathVariable("id") Long id) {
        return R.ok(adminPaperService.adminSelectById(id));
    }

    /**
     * POST /admin/paper/edit — 试卷新建 / 编辑统一端点。
     *
     * <p>分支（PRD AC4）：
     * <ul>
     *   <li>{@code body.id == null} → 新建：INSERT biz_paper (status='0' 草稿，paper_type=1 手工)</li>
     *   <li>{@code body.id != null} → 编辑：UPDATE biz_paper（status 不动）</li>
     * </ul>
     *
     * <p>响应：{@code R<Map<"id", Long>>} — {@code {"code":200,"msg":"操作成功","data":{"id":xxx}}}。
     *
     * <p>鉴权：{@code admin:paper:edit}（写权限）。
     */
    @SaCheckPermission("admin:paper:edit")
    @PostMapping("/edit")
    public R<Map<String, Long>> edit(@RequestBody AdminPaperEditBo bo) {
        Long id = adminPaperService.adminEdit(bo);
        return R.ok(Collections.singletonMap("id", id));
    }

    /**
     * POST /admin/paper/delete/{id} — 试卷软删。
     *
     * <p>业务规则：
     * <ul>
     *   <li>试卷不存在 → 抛 {@code ServiceException("试卷不存在: id")}</li>
     *   <li>已软删 → 抛 {@code ServiceException("试卷已软删，不能重复删除")}</li>
     *   <li>正常 → {@code UPDATE biz_paper SET status='2', update_time=NOW() WHERE id=?}</li>
     * </ul>
     *
     * <p>响应 envelope：成功 {@code R<Void>.ok()} / 失败 {@code R.fail("...")}（全局异常处理器转）。
     *
     * <p>鉴权：{@code admin:paper:edit}（写权限）。
     */
    @SaCheckPermission("admin:paper:edit")
    @PostMapping("/delete/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        adminPaperService.adminSoftDelete(id);
        return R.ok();
    }
}
