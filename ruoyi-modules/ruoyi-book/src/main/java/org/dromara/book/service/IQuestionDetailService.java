package org.dromara.book.service;

import org.dromara.book.domain.vo.QuestionPaperVo;

import java.util.List;

/**
 * 题目详情衍生 Service 接口（PRD §3.1 B-6/B-7/B-8/B-9 共 4 端点）。
 *
 * <ul>
 *   <li>GET /teacher/qd/papers/{id}        → {@link #papers}（真实 JOIN 查询）</li>
 *   <li>GET /teacher/qd/stats/{id}         → mock，controller 直接返</li>
 *   <li>GET /teacher/center/q-folder/tree  → mock，controller 直接返</li>
 *   <li>GET /teacher/question/similar/{id} → mock，controller 直接返</li>
 * </ul>
 *
 * <p>只有 B-6 走 Service / Mapper — 其他 3 个 mock 端点 controller 内联返常量数据。
 *
 * @author backend-dev
 */
public interface IQuestionDetailService {

    /**
     * GET /teacher/qd/papers/{id} — 该题出现在哪些卷。
     *
     * @param questionId 题目 id（null / 非法返空 list，不抛错）
     * @return 卷列表（可能为空），按 create_time DESC 排序
     */
    List<QuestionPaperVo> papers(Long questionId);
}
