package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.service.IPaperBasketService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 试卷筐 Controller — R 卡段① 对称 {@link QuestionBasketController} 复刻。
 *
 * <p>路径前缀 {@code /teacher/exam/paper}，跟 {@link PaperLibraryController} 同 prefix
 * 但拆独立 Controller 更清晰；端点 path 不撞（lazyTree/page/detail/create vs addBasket/cancel/...）。
 *
 * <p>5 端点（misikt 风格，PRD §3.4 / 数据建模 05 line 716 标"M4 端点未实现" → 自定义路径）：
 * <ul>
 *   <li>POST /teacher/exam/paper/addBasket/{id}  — 加筐</li>
 *   <li>POST /teacher/exam/paper/cancel/{id}     — 移筐</li>
 *   <li>POST /teacher/exam/paper/queryBasket     — 看筐（返 List 不分页 — misikt 真实行为）</li>
 *   <li>POST /teacher/exam/paper/empty           — 清筐</li>
 *   <li>POST /teacher/exam/paper/basketNum       — 角标</li>
 * </ul>
 *
 * <p>响应走 {@code MisiktEnvelopeAdvice} 自动转 misikt envelope {@code {code:1, message, response}}。
 * R&lt;T&gt; 路径 message 用 "操作成功"（V1 兼容，回归测试断言依赖） — 走 R 框架不走裸返回。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/exam/paper")
@RequiredArgsConstructor
public class PaperBasketController {

    private final IPaperBasketService paperBasketService;

    /**
     * POST /teacher/exam/paper/addBasket/{id} — 加筐。
     *
     * <p>INSERT IGNORE — 复合主键 (user_id, paper_id) 防重；重复加不报错。
     */
    @SaCheckLogin
    @PostMapping("/addBasket/{id}")
    public R<Void> addBasket(@PathVariable("id") Long id) {
        Long userId = LoginHelper.getUserId();
        paperBasketService.addBasket(userId, id);
        return R.ok();
    }

    /**
     * POST /teacher/exam/paper/cancel/{id} — 单卷移筐。
     */
    @SaCheckLogin
    @PostMapping("/cancel/{id}")
    public R<Void> cancel(@PathVariable("id") Long id) {
        Long userId = LoginHelper.getUserId();
        paperBasketService.cancel(userId, id);
        return R.ok();
    }

    /**
     * POST /teacher/exam/paper/queryBasket — 看筐（不分页）。
     *
     * <p>misikt 真实行为：不分页，返当前用户全部筐卷；FE 接收 {@code PaperBasketItem[]}。
     */
    @SaCheckLogin
    @PostMapping("/queryBasket")
    public R<List<PaperListItemVo>> queryBasket() {
        Long userId = LoginHelper.getUserId();
        List<PaperListItemVo> list = paperBasketService.queryBasket(userId);
        return R.ok(list);
    }

    /**
     * POST /teacher/exam/paper/empty — 清筐。
     */
    @SaCheckLogin
    @PostMapping("/empty")
    public R<Void> empty() {
        Long userId = LoginHelper.getUserId();
        paperBasketService.empty(userId);
        return R.ok();
    }

    /**
     * POST /teacher/exam/paper/basketNum — 角标数量。
     */
    @SaCheckLogin
    @PostMapping("/basketNum")
    public R<Long> basketNum() {
        Long userId = LoginHelper.getUserId();
        long count = paperBasketService.basketNum(userId);
        return R.ok(count);
    }
}
