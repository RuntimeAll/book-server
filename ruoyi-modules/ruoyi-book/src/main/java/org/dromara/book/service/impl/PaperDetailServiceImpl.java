package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizPaperQuestion;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperSectionVo;
import org.dromara.book.domain.vo.PaperSourceQuestionVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.service.IPaperDetailService;
import org.dromara.book.service.IQuestionService;
import org.dromara.book.service.paper.QuestionSnapshotCodec;
import org.dromara.book.service.paper.SelectionRules;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaperDetailServiceImpl implements IPaperDetailService {
    private final BizPaperMapper paperMapper;
    private final BizPaperQuestionMapper paperQuestionMapper;
    private final IQuestionService questionService;
    private final QuestionSnapshotCodec snapshotCodec;

    @Override
    @Transactional(readOnly = true)
    public PaperDetailVo getPaperDetail(Long paperId) {
        if (paperId == null) {
            return null;
        }
        return TenantHelper.ignore(() -> readDetail(paperId));
    }

    private PaperDetailVo readDetail(Long paperId) {
        Long userId = LoginHelper.getUserId();
        PaperDetailVo header = paperMapper.selectPaperDetailHeader(paperId, userId == null ? null : userId.toString());
        if (header == null) {
            return null;
        }
        header.setCanManage(SelectionRules.canManagePaper(header.getCreateBy(), userId));
        List<PaperSectionVo> sections = paperMapper.selectSectionsByPaperId(paperId);
        List<BizPaperQuestion> rows = paperQuestionMapper.selectList(new LambdaQueryWrapper<BizPaperQuestion>()
            .eq(BizPaperQuestion::getPaperId, paperId)
            .orderByAsc(BizPaperQuestion::getSort).orderByAsc(BizPaperQuestion::getId));
        List<Long> legacyIds = rows.stream().filter(row -> row.getSnapshotJson() == null)
            .map(BizPaperQuestion::getQuestionId).distinct().toList();
        Map<Long, QuestionDetailVo> originals = new HashMap<>();
        // Existing rows are read, never migrated; newly saved instances never fall back to source questions.
        for (int start = 0; start < legacyIds.size(); start += SelectionRules.MAX_BATCH_SIZE) {
            List<Long> batch = legacyIds.subList(start, Math.min(start + SelectionRules.MAX_BATCH_SIZE, legacyIds.size()));
            for (QuestionDetailVo question : questionService.listByIds(batch)) {
                originals.put(question.getId(), question);
            }
        }
        Map<Long, List<PaperSourceQuestionVo>> bySection = new HashMap<>();
        for (BizPaperQuestion row : rows) {
            QuestionDetailVo snapshot = row.getSnapshotJson() == null ? originals.get(row.getQuestionId())
                : snapshotCodec.decode(row.getSnapshotJson());
            if (snapshot == null) {
                continue;
            }
            PaperSourceQuestionVo question = new PaperSourceQuestionVo();
            BeanUtils.copyProperties(snapshot, question);
            question.setId(row.getQuestionId());
            question.setPaperQuestionId(row.getId());
            question.setSourceBookId(row.getSourceBookId());
            question.setSourceItemId(row.getSourceItemId());
            question.setSort(row.getSort());
            question.setPqScore(row.getScore());
            bySection.computeIfAbsent(row.getSectionId(), unused -> new ArrayList<>()).add(question);
        }
        for (PaperSectionVo section : sections) {
            section.setQuestions(bySection.getOrDefault(section.getSectionId(), List.of()));
        }
        header.setSections(sections);
        return header;
    }
}
