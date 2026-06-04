package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.FavoriteToggleVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.service.IFavoriteService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 题目收藏 Controller（PRD §3.1 B-1/B-2/B-3 共 3 端点）。
 *
 * <p>路径前缀 {@code /teacher/qd/favorite/{id}}，命中 {@link MisiktEnvelopeAdvice}
 * 自动包 {@code {code:1, message:"成功", response:<T>}} envelope — Controller 直接返业务对象 / boolean / null。
 *
 * <p>3 端点：
 * <ul>
 *   <li>GET    /teacher/qd/favorite/{id} — 查当前用户是否已收藏 → {@code boolean}</li>
 *   <li>POST   /teacher/qd/favorite/{id} — Toggle 收藏（body 可带 {folderId}）→ {@link FavoriteToggleVo}</li>
 *   <li>DELETE /teacher/qd/favorite/{id} — 显式取消（幂等）→ {@code null}</li>
 * </ul>
 *
 * <p>鉴权：{@code @SaCheckLogin}，user_id 走 {@code LoginHelper.getUserId()}。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/qd/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final IFavoriteService favoriteService;

    /**
     * GET /teacher/qd/favorite/page — 分页查当前用户收藏题（PRD-A-005 T5）。
     *
     * <p>入参 query string {@code pageNum / pageSize / folderId?}；userId 走 {@code LoginHelper.getUserId()}
     * 不从外部传（防越权）。出参复用题库题 VO {@link QuestionItemVo} + 分页包装 {@link MisiktPageVo}，
     * 方便 FE 复用 QuestionCard 渲染。命中 {@link MisiktEnvelopeAdvice} 自动包 envelope。
     *
     * <p>⚠️ {@code /page} 是静态路径，Spring 优先匹配于 {@code /{id}}（路径变量），不会冲突。
     *
     * @param pageNum  页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param folderId 收藏夹 id（可空，不传则不按夹过滤）
     * @return misikt 风格分页 VO（收藏题列表）
     */
    @SaCheckLogin
    @GetMapping("/page")
    public MisiktPageVo<QuestionItemVo> page(@RequestParam(value = "pageNum", required = false) Integer pageNum,
                                             @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                             @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = LoginHelper.getUserId();
        return favoriteService.page(userId, pageNum, pageSize, folderId);
    }

    /**
     * GET /teacher/qd/favorite/{id} — 当前用户对该题是否已收藏。
     *
     * @param id 题目 id
     * @return 已收藏 true / 未收藏 false
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public Boolean isFavorite(@PathVariable("id") Long id) {
        Long userId = LoginHelper.getUserId();
        return favoriteService.isFavorite(userId, id);
    }

    /**
     * POST /teacher/qd/favorite/{id} — Toggle 收藏。
     *
     * <p>body 可带 {@code {folderId: 1}}（指定收藏夹），FE 也可能不传 body —
     * 用 {@code @RequestBody(required=false) Map} 双兼容。
     *
     * @param id   题目 id
     * @param body 可空，可含 folderId（Number）
     * @return {@link FavoriteToggleVo}（toggle 之后状态）
     */
    @SaCheckLogin
    @PostMapping("/{id}")
    public FavoriteToggleVo toggle(@PathVariable("id") Long id,
                                   @RequestBody(required = false) Map<String, Object> body) {
        Long userId = LoginHelper.getUserId();
        Long folderId = parseFolderId(body);
        return favoriteService.toggle(userId, id, folderId);
    }

    /**
     * DELETE /teacher/qd/favorite/{id} — 显式取消收藏（幂等）。
     *
     * @param id 题目 id
     * @return null（advice 自动转 {@code {code:1, message:"成功", response:null}}）
     */
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public Object cancel(@PathVariable("id") Long id) {
        Long userId = LoginHelper.getUserId();
        favoriteService.cancel(userId, id);
        return null;
    }

    /**
     * 从 body 解 folderId — 兼容 Number / String / null / key 缺失 4 种场景。
     */
    private Long parseFolderId(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object raw = body.get("folderId");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
