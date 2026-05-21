package org.dromara.book.service;

import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.vo.ExamDataVo;
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


    /**
     * 组卷草稿（POST /teacher/question/genExamData/）。
     *
     * <p>取当前用户试题筐内全部已发布题（status='1'），按 questionType 分组生成 sections —
     * 1=选择/4=填空/5=简答，按 misikt 真实行为固定顺序 1→4→5；不出现的题型不返。
     *
     * <p>草稿不落库，跟 misikt 一致（FE 工作台本地 state 操作）。
     *
     * @param userId 当前用户 ID（不可空）
     * @return 草稿 VO（sections 按 questionType 顺序，空筐返 sections=[]）
     */
    ExamDataVo genExamData(Long userId);
}
