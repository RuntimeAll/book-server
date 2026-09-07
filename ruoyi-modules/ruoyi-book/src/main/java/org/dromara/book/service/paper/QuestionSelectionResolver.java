package org.dromara.book.service.paper;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SelectionReferenceBo;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.QuestionSelectionMapper;
import org.dromara.book.mapper.QuestionSelectionMapper.ShelfSelectionRow;
import org.dromara.book.service.IQuestionService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionSelectionResolver {
    private final QuestionSelectionMapper selectionMapper;
    private final IQuestionService questionService;
    private final QuestionSnapshotCodec snapshotCodec;

    public List<BasketEntryVo> resolve(List<? extends SelectionReferenceBo> references, Long userId,
                                       boolean superAdmin) {
        Map<Long, ShelfSelectionRow> items = validateReferences(references, userId, superAdmin);
        List<Long> ids = references.stream().map(SelectionReferenceBo::getQuestionId).distinct().toList();
        Map<Long, QuestionDetailVo> questions = new HashMap<>();
        for (int start = 0; start < ids.size(); start += SelectionRules.MAX_BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + SelectionRules.MAX_BATCH_SIZE, ids.size()));
            for (QuestionDetailVo question : questionService.listByIds(batch)) {
                questions.put(question.getId(), question);
            }
        }
        List<BasketEntryVo> result = new ArrayList<>(references.size());
        for (SelectionReferenceBo ref : references) {
            QuestionDetailVo question = questions.get(ref.getQuestionId());
            if (question == null || !"1".equals(question.getStatus())) {
                throw new ServiceException("题目不存在或不可用: " + ref.getQuestionId(), 400);
            }
            BasketEntryVo entry = new BasketEntryVo();
            entry.setEntryKey(SelectionRules.entryKey(ref));
            entry.setQuestionId(ref.getQuestionId());
            entry.setSourceBookId(ref.getSourceBookId());
            entry.setSourceItemId(ref.getSourceItemId());
            String overrides = ref.getSourceItemId() == null ? null : items.get(ref.getSourceItemId()).getOverrideJson();
            entry.setQuestion(snapshotCodec.resolve(question, overrides));
            result.add(entry);
        }
        return result;
    }

    public void validate(List<? extends SelectionReferenceBo> references, Long userId, boolean superAdmin) {
        validateReferences(references, userId, superAdmin);
        List<Long> ids = references.stream().map(SelectionReferenceBo::getQuestionId).distinct().toList();
        if (selectionMapper.selectAvailableQuestionIds(ids).size() != ids.size()) {
            throw new ServiceException("题目不存在或不可用", 400);
        }
    }

    private Map<Long, ShelfSelectionRow> validateReferences(List<? extends SelectionReferenceBo> references,
                                                           Long userId, boolean superAdmin) {
        if (userId == null) {
            throw new ServiceException("请先登录", 401);
        }
        if (references == null || references.isEmpty() || references.size() > SelectionRules.MAX_PAPER_QUESTIONS) {
            throw new ServiceException("题目数量无效", 400);
        }
        Map<String, SelectionReferenceBo> unique = new LinkedHashMap<>();
        for (SelectionReferenceBo ref : references) {
            String key = SelectionRules.entryKey(ref);
            SelectionReferenceBo prior = unique.putIfAbsent(key, ref);
            if (prior != null && (!Objects.equals(prior.getQuestionId(), ref.getQuestionId())
                || !Objects.equals(prior.getSourceBookId(), ref.getSourceBookId()))) {
                throw new ServiceException("同一实例的来源不一致", 400);
            }
        }
        List<Long> originalIds = unique.values().stream().filter(ref -> ref.getSourceItemId() == null)
            .map(SelectionReferenceBo::getQuestionId).distinct().toList();
        Set<Long> readableIds = originalIds.isEmpty() ? Set.of()
            : new HashSet<>(selectionMapper.selectReadableQuestionIds(originalIds, userId, superAdmin));
        for (Long id : originalIds) {
            if (!readableIds.contains(id)) {
                throw new ServiceException("题目不存在、不可用或无权使用: " + id, 400);
            }
        }
        List<Long> itemIds = unique.values().stream().map(SelectionReferenceBo::getSourceItemId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, ShelfSelectionRow> items = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (ShelfSelectionRow row : selectionMapper.selectShelfItems(itemIds)) {
                items.put(row.getId(), row);
            }
        }
        for (SelectionReferenceBo ref : unique.values()) {
            if (ref.getSourceItemId() != null) {
                ShelfSelectionRow item = items.get(ref.getSourceItemId());
                if (item == null || !Objects.equals(item.getBookId(), ref.getSourceBookId())
                    || !Objects.equals(item.getQuestionId(), ref.getQuestionId()) || !"question".equals(item.getKind())
                    || !"0".equals(item.getBookStatus())) {
                    throw new ServiceException("书内题目来源无效", 400);
                }
                if (!Integer.valueOf(1).equals(item.getIsPublic()) && !Objects.equals(item.getOwnerId(), userId)
                    && !superAdmin) {
                    throw new ServiceException("无权使用该书内题目", 403);
                }
            }
        }
        return items;
    }
}
