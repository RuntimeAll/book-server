package org.dromara.book.service;

import org.dromara.book.domain.vo.FavoriteToggleVo;

/**
 * 题目收藏 Service 接口（PRD §3.1 B-1/B-2/B-3 共 3 个端点）。
 *
 * <ul>
 *   <li>GET    /teacher/qd/favorite/{id} → {@link #isFavorite}</li>
 *   <li>POST   /teacher/qd/favorite/{id} → {@link #toggle}（带可空 folderId）</li>
 *   <li>DELETE /teacher/qd/favorite/{id} → {@link #cancel}（幂等）</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface IFavoriteService {

    /**
     * 当前用户是否已收藏该题。
     *
     * @param userId     当前用户 id
     * @param questionId 题目 id
     * @return true=已收藏 / false=未收藏（或入参非法）
     */
    boolean isFavorite(Long userId, Long questionId);

    /**
     * Toggle 收藏（存在 → 删 → false / 不存在 → 插 → true）。
     *
     * @param userId     当前用户 id
     * @param questionId 题目 id
     * @param folderId   收藏夹 id（可空，null 时落 0=默认夹）
     * @return toggle 之后的状态
     */
    FavoriteToggleVo toggle(Long userId, Long questionId, Long folderId);

    /**
     * 显式取消收藏（物理 DELETE，幂等 — 未收藏也返成功不抛错）。
     *
     * @param userId     当前用户 id
     * @param questionId 题目 id
     */
    void cancel(Long userId, Long questionId);
}
