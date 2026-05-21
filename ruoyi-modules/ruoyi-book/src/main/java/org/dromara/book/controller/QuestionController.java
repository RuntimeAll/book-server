package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.vo.ExamDataVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.service.IQuestionService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目 Controller（/teacher/question/page + /teacher/question/select/{id}）。
 *
 * <p>路径前缀 {@code /teacher/question}，命中 {@link MisiktEnvelopeAdvice} 自动转 envelope。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/question")
@RequiredArgsConstructor
public class QuestionController {

    private final IQuestionService questionService;

    /**
     * POST /teacher/question/page — 分页拉题。
     *
     * <p>入参 BO 字段命名严格按 misikt（pageIndex 不是 pageNum）；响应 PageHelper 完整结构。
     */
    @SaCheckLogin
    @PostMapping("/page")
    public R<MisiktPageVo<QuestionItemVo>> page(@RequestBody(required = false) QuestionPageBo bo) {
        if (bo == null) {
            bo = new QuestionPageBo();
        }
        return R.ok(questionService.page(bo));
    }

    /**
     * POST /teacher/question/select/{id} — 单题详情。
     *
     * <p>响应含 questionKnowledges（U 轨）+ questionStdKnowledges（S 轨）。
     */
    @SaCheckLogin
    @PostMapping("/select/{id}")
    public R<QuestionDetailVo> select(@PathVariable("id") Long id) {
        return R.ok(questionService.selectById(id));
    }


    /**
     * POST /teacher/question/genExamData/ — 组卷草稿。
     *
     * <p>无入参（path 末尾 trailing slash 是 misikt 真实特征 — FE TS 契约同样带斜杠）；
     * 拿当前用户筐内全部已发布题，按 questionType 1→4→5 分组返 sections，
     * 草稿不落库（FE 工作台本地 state）。
     */
    @SaCheckLogin
    @PostMapping("/genExamData/")
    public R<ExamDataVo> genExamData() {
        Long userId = LoginHelper.getUserId();
        return R.ok(questionService.genExamData(userId));
    }
}
