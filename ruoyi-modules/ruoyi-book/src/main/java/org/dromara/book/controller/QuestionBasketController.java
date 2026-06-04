package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.service.IQuestionBasketService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 试题筐 Controller（5 端点）。
 *
 * <p>路径前缀 {@code /teacher/question}，跟 {@link QuestionController} 同 prefix
 * 但拆独立 Controller 更清晰，FE 无感（端点 path 不变）。
 *
 * <p>5 端点：
 * <ul>
 *   <li>POST /teacher/question/addBasket/{id}  — 加筐</li>
 *   <li>POST /teacher/question/cancel/{id}     — 移筐</li>
 *   <li>POST /teacher/question/queryBasket     — 看筐（返 List 不分页 — misikt 真实行为）</li>
 *   <li>POST /teacher/question/empty           — 清筐</li>
 *   <li>POST /teacher/question/basketNum       — 角标</li>
 * </ul>
 *
 * <p>响应走 {@link MisiktEnvelopeAdvice} 自动转 misikt envelope {@code {code:1, message, response}}。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/question")
@RequiredArgsConstructor
public class QuestionBasketController {

    private final IQuestionBasketService questionBasketService;

    /**
     * POST /teacher/question/addBasket/{id} — 加筐。
     *
     * <p>INSERT IGNORE — 复合主键 (user_id, question_id) 防重；重复加不报错。
     *
     * <p>🔴 鉴权 = 仅 {@code @SaCheckLogin}（登录即可）。加入试题栏是教师组卷的核心高频动作，
     * 不作权限受限点 —— 早前（PRD-A-005 T2）误把本端点当"页面级权限示范受限点"叠加
     * {@code @SaCheckRole("teacher")}，导致 superadmin 等未绑 teacher 角色的登录用户点
     * "全部加入试题栏"被 403 拦死（FE 全局拦截器报"系统内部错误"）。权限机制示范改由
     * 前端路由守卫 /admin/console（meta.roles=superadmin）承担，不再用核心业务端点硬造受限点。
     */
    @SaCheckLogin
    @PostMapping("/addBasket/{id}")
    public R<Void> addBasket(@PathVariable("id") Long id) {
        Long userId = LoginHelper.getUserId();
        questionBasketService.addBasket(userId, id);
        return R.ok();
    }

    /**
     * POST /teacher/question/cancel/{id} — 单题移筐。
     */
    @SaCheckLogin
    @PostMapping("/cancel/{id}")
    public R<Void> cancel(@PathVariable("id") Long id) {
        Long userId = LoginHelper.getUserId();
        questionBasketService.cancel(userId, id);
        return R.ok();
    }

    /**
     * POST /teacher/question/queryBasket — 看筐（不分页）。
     *
     * <p>misikt 真实行为：不分页，返当前用户全部筐题；FE 接收 {@code BasketItem[]}。
     */
    @SaCheckLogin
    @PostMapping("/queryBasket")
    public R<List<QuestionItemVo>> queryBasket() {
        Long userId = LoginHelper.getUserId();
        List<QuestionItemVo> list = questionBasketService.queryBasket(userId);
        return R.ok(list);
    }

    /**
     * POST /teacher/question/empty — 清筐。
     */
    @SaCheckLogin
    @PostMapping("/empty")
    public R<Void> empty() {
        Long userId = LoginHelper.getUserId();
        questionBasketService.empty(userId);
        return R.ok();
    }

    /**
     * POST /teacher/question/basketNum — 角标数量。
     */
    @SaCheckLogin
    @PostMapping("/basketNum")
    public R<Long> basketNum() {
        Long userId = LoginHelper.getUserId();
        long count = questionBasketService.basketNum(userId);
        return R.ok(count);
    }
}
