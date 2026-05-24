package org.dromara.book.service;

import org.dromara.book.domain.vo.PaperListItemVo;

import java.util.List;

/**
 * 试卷筐 Service 接口 — R 卡段① 对称试题筐复刻。
 *
 * <p>对应 5 个 misikt 风格端点（PRD §3.4 — 数据建模 05 line 716 标 misikt 真实端点未实现，自定义路径）：
 * <ul>
 *   <li>POST /teacher/exam/paper/addBasket/{id}  → {@link #addBasket}</li>
 *   <li>POST /teacher/exam/paper/cancel/{id}     → {@link #cancel}</li>
 *   <li>POST /teacher/exam/paper/queryBasket     → {@link #queryBasket}</li>
 *   <li>POST /teacher/exam/paper/empty           → {@link #empty}</li>
 *   <li>POST /teacher/exam/paper/basketNum       → {@link #basketNum}</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface IPaperBasketService {

    /**
     * 加卷入筐（INSERT IGNORE，复合主键 user_id+paper_id 防重）。
     *
     * @param userId  当前用户 ID
     * @param paperId 试卷 ID
     */
    void addBasket(Long userId, Long paperId);

    /**
     * 单卷移筐（物理 DELETE）。
     *
     * @param userId  当前用户 ID
     * @param paperId 试卷 ID
     */
    void cancel(Long userId, Long paperId);

    /**
     * 看筐 — 返当前用户全部筐卷（按 add_time 倒序，含卷头字段 PaperListItemVo）。
     *
     * <p>misikt 真实行为：不分页，一次返全部（FE TS 类型为 {@code PaperBasketItem[]}，对称试题筐
     * {@code BasketItem[]}）。
     *
     * @param userId 当前用户 ID
     * @return 试卷 VO 列表
     */
    List<PaperListItemVo> queryBasket(Long userId);

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
     * @return 筐卷数量
     */
    long basketNum(Long userId);
}
