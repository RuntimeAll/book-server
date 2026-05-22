package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.FreeTagVo;
import org.dromara.book.domain.vo.PaperSourceQuestionVo;
import org.dromara.book.domain.vo.PaperSourceVo;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizQuestionFreeTagMapper;
import org.dromara.book.service.IPaperSourceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 原卷预览 Service 实现（PRD §3.1 B-10）。
 *
 * <p>两步查询：
 * <ol>
 *   <li>{@link BizPaperMapper#selectPaperHeaderById} — 卷头（不存在 / 软删 → 返 null）</li>
 *   <li>{@link BizPaperMapper#selectQuestionsByPaperId} — 卷下所有已发布题（可能为空）</li>
 * </ol>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class PaperSourceServiceImpl implements IPaperSourceService {

    private final BizPaperMapper bizPaperMapper;
    private final BizQuestionFreeTagMapper bizQuestionFreeTagMapper;

    @Override
    public PaperSourceVo getPaperSource(Long paperId) {
        if (paperId == null) {
            return null;
        }
        // step 1 — 卷头（不存在 / status≠'1' → null）
        PaperSourceVo header = bizPaperMapper.selectPaperHeaderById(paperId);
        if (header == null) {
            return null;
        }
        // step 2 — 卷下题列表（可能为空 list）
        List<PaperSourceQuestionVo> questions = bizPaperMapper.selectQuestionsByPaperId(paperId);
        if (questions == null || questions.isEmpty()) {
            header.setQuestions(Collections.emptyList());
            return header;
        }
        // step 3 — 批量回填 freeTags（X 卡段②）
        List<Long> qids = questions.stream().map(PaperSourceQuestionVo::getId).collect(Collectors.toList());
        Map<Long, List<FreeTagVo>> ftMap = loadFreeTagsByQuestionIds(qids);
        for (PaperSourceQuestionVo q : questions) {
            q.setFreeTags(ftMap.getOrDefault(q.getId(), Collections.emptyList()));
        }
        header.setQuestions(questions);
        return header;
    }

    /**
     * 批量按 question_id 拉 freeTags 并按 questionId 分组（X 卡段②）。
     *
     * <p>跟 {@code QuestionServiceImpl} / {@code QuestionBasketServiceImpl} 同源实现 —
     * 第三处复用，可考虑抽公共 helper（V0.1 暂保留三处独立，避免引跨 service 工具类）。
     */
    private Map<Long, List<FreeTagVo>> loadFreeTagsByQuestionIds(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BizQuestionFreeTagMapper.FreeTagWithQid> all =
            bizQuestionFreeTagMapper.selectGroupedByQuestionIds(questionIds);
        if (all == null || all.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<FreeTagVo>> grouped = new HashMap<>();
        for (BizQuestionFreeTagMapper.FreeTagWithQid row : all) {
            FreeTagVo pure = new FreeTagVo();
            pure.setId(row.getId());
            pure.setName(row.getName());
            pure.setPosition(row.getPosition());
            grouped.computeIfAbsent(row.getQuestionId(), k -> new ArrayList<>()).add(pure);
        }
        return grouped;
    }
}
