package org.dromara.book.service;

import org.dromara.book.domain.vo.QuestionItemVo;

import java.util.List;

/**
 * 试题筐 Service 接口。
 *
 * <p>对应 5 个 misikt 端点：
 * <ul>
 *   <li>POST /teacher/question/addBasket/{id}  → {@link #addBasket}</li>
 *   <li>POST /teacher/question/cancel/{id}     → {@link #cancel}</li>
 *   <li>POST /teacher/question/queryBasket     → {@link #queryBasket}</li>
 *   <li>POST /teacher/question/empty           → {@link #empty}</li>
 *   <li>POST /teacher/question/basketNum       → {@link #basketNum}</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface IQuestionBasketService {

    /**
     * 加题入筐（INSERT IGNORE，复合主键 user_id+question_id 防重）。
     *
     * @param userId     当前用户 ID
     * @param questionId 题目 ID
     */
    void addBasket(Long userId, Long questionId);

    /**
     * 单题移筐（物理 DELETE）。
     *
     * @param userId     当前用户 ID
     * @param questionId 题目 ID
     */
    void cancel(Long userId, Long questionId);

    /**
     * 看筐 — 返当前用户全部筐题（按 add_time 倒序，含 questionKnowledges 回填）。
     *
     * <p>misikt 真实行为：不分页，一次返全部（FE TS 类型为 {@code BasketItem[]}）。
     *
     * @param userId 当前用户 ID
     * @return 题目 VO 列表
     */
    List<QuestionItemVo> queryBasket(Long userId);

    /**
     * 清空当前用户全筐。
     *
     * @param userId 当前用户 ID
     */
    void empty(Long userId);

    /**
     * 角标数量。
     *
     * @param userId 当前用户 ID
     * @return 筐题数量
     */
    long basketNum(Long userId);
}
