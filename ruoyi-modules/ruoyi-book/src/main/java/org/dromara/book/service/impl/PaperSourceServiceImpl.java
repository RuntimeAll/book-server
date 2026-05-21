package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.PaperSourceQuestionVo;
import org.dromara.book.domain.vo.PaperSourceVo;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.service.IPaperSourceService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

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
        header.setQuestions(questions != null ? questions : Collections.emptyList());
        return header;
    }
}
