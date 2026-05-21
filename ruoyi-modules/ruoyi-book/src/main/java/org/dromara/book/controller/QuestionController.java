package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.service.IQuestionService;
import org.dromara.common.core.domain.R;
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
}
