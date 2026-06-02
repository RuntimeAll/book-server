package org.dromara.book.service;

import org.dromara.book.domain.vo.FavoriteToggleVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionItemVo;

/**
 * 题目收藏 Service 接口（PRD §3.1 B-1/B-2/B-3 + PRD-A-005 T5 page 共 4 个端点）。
 *
 * <ul>
 *   <li>GET    /teacher/qd/favorite/{id}   → {@link #isFavorite}</li>
 *   <li>POST   /teacher/qd/favorite/{id}   → {@link #toggle}（带可空 folderId）</li>
 *   <li>DELETE /teacher/qd/favorite/{id}   → {@link #cancel}（幂等）</li>
 *   <li>GET    /teacher/qd/favorite/page   → {@link #page}（分页 + folderId 筛选，限当前用户）</li>
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

    /**
     * PRD-A-005 T5 — 分页查当前用户收藏题（GET /teacher/qd/favorite/page）。
     *
     * <p>biz_question_favorite INNER JOIN biz_question 限 {@code userId}（防越权）；
     * folderId 非空按收藏夹过滤。复用题库题 VO {@link QuestionItemVo} + 分页包装 {@link MisiktPageVo}，
     * 方便 FE 复用 QuestionCard。is_favorite 恒 1，freeTags 二次回填（与题库 page 同款）。
     *
     * @param userId   当前登录用户 id（不可空，防越权）
     * @param pageNum  页码（&lt;=0 兜底 1）
     * @param pageSize 每页条数（&lt;=0 兜底 10）
     * @param folderId 收藏夹 id（可空，null 时不按夹过滤）
     * @return misikt 风格分页 VO（题项）
     */
    MisiktPageVo<QuestionItemVo> page(Long userId, Integer pageNum, Integer pageSize, Long folderId);
}
