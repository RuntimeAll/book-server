package org.dromara.book.service;

import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.vo.ExamDataVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;

import java.util.List;

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
     * Q' 卡段① — 批量按 id 拉题目详情（GET /teacher/question/list?ids=）。
     *
     * <p>用于试卷预览 PDF 导出场景。返回字段含
     * answer / explain / stemImg / options / freeTags / questionKnowledges，FE 渲染必须的全集。
     *
     * <p>实现要点：
     * <ul>
     *   <li>软删过滤 status&lt;&gt;'2'</li>
     *   <li>按入参 ids 顺序重排（LinkedHashMap by id，不依赖 SQL FIND_IN_SET）</li>
     *   <li>复用 page/select 现有 loadKnowledgesByQuestionIds + loadFreeTagsByQuestionIds</li>
     * </ul>
     *
     * @param ids 题目 ID 列表（非空，上限 Controller 兜底 100）
     * @return 详情 VO 列表（按入参顺序，软删题被剔除导致长度可能小于入参）
     */
    List<QuestionDetailVo> listByIds(List<Long> ids);

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
