package org.dromara.book.service;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionBatchBo;
import org.dromara.book.domain.vo.QuestionBatchVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.QuestionSelectionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionBatchService {
    private final IQuestionService questionService;
    private final QuestionSelectionMapper selectionMapper;

    public QuestionBatchVo query(QuestionBatchBo bo, Long userId, boolean superAdmin) {
        if (userId == null) {
            throw new ServiceException("请先登录", 401);
        }
        if (bo == null || bo.getIds() == null || bo.getIds().size() > QuestionBatchBo.MAX_IDS
            || bo.getIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ServiceException("题目批查最多100个有效ID", 400);
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(bo.getIds()));
        if (ids.isEmpty()) {
            return new QuestionBatchVo(List.of(), List.of());
        }
        Map<Long, QuestionDetailVo> byId = new LinkedHashMap<>();
        List<Long> readableIds = selectionMapper.selectReadableQuestionIds(ids, userId, superAdmin);
        if (!readableIds.isEmpty()) {
            for (QuestionDetailVo question : questionService.listByIds(readableIds)) {
                byId.put(question.getId(), question);
            }
        }
        List<QuestionDetailVo> items = new ArrayList<>();
        List<String> missingIds = new ArrayList<>();
        for (Long id : ids) {
            QuestionDetailVo question = byId.get(id);
            if (question == null) {
                missingIds.add(id.toString());
            } else {
                items.add(question);
            }
        }
        return new QuestionBatchVo(items, missingIds);
    }
}
