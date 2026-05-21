package org.dromara.book.service;

import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;

/**
 * 题目 Service 接口。
 *
 * @author backend-dev
 */
public interface IQuestionService {

    /**
     * 分页查询题目（POST /teacher/question/page）。
     *
     * @param bo 分页 + 筛选入参（misikt 风格 pageIndex / keyWord / difficult / 等）
     * @return misikt 风格分页 VO
     */
    MisiktPageVo<QuestionItemVo> page(QuestionPageBo bo);

    /**
     * 单题详情查询（POST /teacher/question/select/{id}）。
     *
     * @param id 题目 ID
     * @return 详情 VO（含 questionKnowledges + questionStdKnowledges）；不存在返 null
     */
    QuestionDetailVo selectById(Long id);
}
