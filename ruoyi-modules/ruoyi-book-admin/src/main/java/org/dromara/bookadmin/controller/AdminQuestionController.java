package org.dromara.bookadmin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.bookadmin.service.IAdminQuestionService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * book-admin 后台题目管理 Controller（H1 卡 段② BE 波 2 — 独立模块化重构）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本 Controller 物理落 {@code ruoyi-book-admin}
 * 模块，禁注入 {@code org.dromara.book.service.IQuestionService}（教师端 Service）；
 * 必须注入 admin 自有 {@link IAdminQuestionService}，方法内部走 Mapper 自查。
 * admin 与 teacher 改方法互不波及。
 *
 * <p>路径前缀 {@code /admin/question/...}，命中 RuoYi 标准 {@code R<T>} envelope，
 * <strong>不</strong>走 {@code MisiktEnvelopeAdvice}（只 scope {@code /teacher/} 前缀）；
 * book-admin (plus-ui) axios 拦截器天然走 RuoYi envelope，免转换。
 *
 * <p>鉴权：每个方法独立挂 {@code @SaCheckPermission("admin:question:xxx")} 注解，
 * 权限标识在 sys_menu 表维护（V6 SQL 已挂 admin:question:list）。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/admin/question")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final IAdminQuestionService adminQuestionService;

    /**
     * POST /admin/question/page — 题目分页（admin 通道，独立 service 自查 Mapper）。
     *
     * <p>本波（V-5 重构）：路由 + Sa-Token 权限注解 + admin 独立 service 全部就绪，
     * 入参 BO + 返回 VO 与教师端共享（DDL 单源），但 service 方法独立维护。
     *
     * <p>响应 envelope：RuoYi 标准 {@code {code:200, msg:"操作成功", data:{records,total,...}}}。
     */
    @SaCheckPermission("admin:question:list")
    @PostMapping("/page")
    public R<MisiktPageVo<QuestionItemVo>> page(@RequestBody(required = false) QuestionPageBo bo) {
        if (bo == null) {
            bo = new QuestionPageBo();
        }
        return R.ok(adminQuestionService.adminPage(bo));
    }
}
