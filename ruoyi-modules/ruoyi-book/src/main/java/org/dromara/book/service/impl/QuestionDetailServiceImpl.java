package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.QuestionPaperVo;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.service.IQuestionDetailService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 题目详情衍生 Service 实现。
 *
 * <p>本类只实现 B-6 papers — B-7/B-8/B-9 mock 在 controller 内联，
 * 不走 service，避免空壳方法污染接口。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class QuestionDetailServiceImpl implements IQuestionDetailService {

    private final BizPaperQuestionMapper bizPaperQuestionMapper;

    @Override
    public List<QuestionPaperVo> papers(Long questionId) {
        if (questionId == null) {
            return Collections.emptyList();
        }
        List<QuestionPaperVo> rows = bizPaperQuestionMapper.selectPapersByQuestionId(questionId);
        return rows != null ? rows : Collections.emptyList();
    }
}
