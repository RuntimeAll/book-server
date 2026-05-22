package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.FreeTagVo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperSectionVo;
import org.dromara.book.domain.vo.PaperSourceQuestionVo;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizQuestionFreeTagMapper;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.service.IPaperDetailService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 试卷详情 Service 实现（E 卡段② — POST /teacher/exam/paper/detail）。
 *
 * <p>三步查询 + 内存分组：
 * <ol>
 *   <li>{@link BizPaperMapper#selectPaperDetailHeader} — 卷头（不存在 / status≠'1' → 返 null）</li>
 *   <li>{@link BizPaperMapper#selectSectionsByPaperId} — sections（按 sort ASC）</li>
 *   <li>{@link BizPaperMapper#selectQuestionsByPaperIdWithSection} — 所有题（含 sectionId，按 pq.sort ASC）</li>
 *   <li>批量回填 freeTags + questionKnowledges（U 轨）</li>
 *   <li>按 section_id 把题分组到对应 PaperSectionVo</li>
 * </ol>
 *
 * <p>设计要点：
 * <ul>
 *   <li>questionKnowledges 走 source='U' —— 跟 page/select 端点行为一致（详情独立页不返 S 轨标准库标注）</li>
 *   <li>QuestionWithSectionId → PaperSourceQuestionVo 走 BeanUtils.copyProperties —— sectionId 不进响应 JSON</li>
 *   <li>section.questions 顺序自然为 pq.sort ASC（mapper 已 ORDER BY，分组时 LinkedHashMap 保序无关，
 *       Service 二次按 sectionId 分桶后即拿到该 section 下题的真实出现顺序 = pq.sort ASC）</li>
 *   <li>段① §3 真数据：paper 2798 应返 sections.length=3，sectionId=3678/3679/3680，
 *       题数 10/6/8，总 24 题 120 分</li>
 * </ul>
 *
 * @author backend-dev (E 卡段②)
 */
@Service
@RequiredArgsConstructor
public class PaperDetailServiceImpl implements IPaperDetailService {

    private final BizPaperMapper bizPaperMapper;
    private final BizQuestionKnowledgeMapper bizQuestionKnowledgeMapper;
    private final BizQuestionFreeTagMapper bizQuestionFreeTagMapper;

    @Override
    public PaperDetailVo getPaperDetail(Long paperId) {
        if (paperId == null) {
            return null;
        }

        // step 1 — 卷头（不存在 / status≠'1' → null）
        PaperDetailVo header = bizPaperMapper.selectPaperDetailHeader(paperId);
        if (header == null) {
            return null;
        }

        // step 2 — sections（按 sort ASC，可能跳号 1/3/4）
        List<PaperSectionVo> sections = bizPaperMapper.selectSectionsByPaperId(paperId);
        if (sections == null || sections.isEmpty()) {
            header.setSections(Collections.emptyList());
            return header;
        }

        // step 3 — 所有题（含 sectionId，按 pq.sort ASC）
        List<BizPaperMapper.QuestionWithSectionId> rawQuestions =
            bizPaperMapper.selectQuestionsByPaperIdWithSection(paperId);

        // step 4 — 批量回填 freeTags + questionKnowledges
        if (rawQuestions != null && !rawQuestions.isEmpty()) {
            List<Long> qids = rawQuestions.stream()
                .map(PaperSourceQuestionVo::getId)
                .collect(Collectors.toList());
            Map<Long, List<FreeTagVo>> ftMap = loadFreeTagsByQuestionIds(qids);
            Map<Long, List<QuestionKnowledgeVo>> kgMap = loadKnowledgesByQuestionIds(qids, "U");
            for (BizPaperMapper.QuestionWithSectionId q : rawQuestions) {
                q.setFreeTags(ftMap.getOrDefault(q.getId(), Collections.emptyList()));
                q.setQuestionKnowledges(kgMap.getOrDefault(q.getId(), Collections.emptyList()));
            }
        }

        // step 5 — 按 section_id 分桶；剥离 sectionId 字段（避免序列化到响应）
        Map<Long, List<PaperSourceQuestionVo>> bySection = new HashMap<>(sections.size() * 2);
        if (rawQuestions != null) {
            for (BizPaperMapper.QuestionWithSectionId raw : rawQuestions) {
                Long sid = raw.getSectionId();
                if (sid == null) {
                    continue;        // 防御 —— biz_paper_question.section_id NOT NULL，正常不会进
                }
                PaperSourceQuestionVo pure = new PaperSourceQuestionVo();
                BeanUtils.copyProperties(raw, pure);  // sectionId 是 raw 子类字段，pure 没有该属性 —— 自然丢弃
                bySection.computeIfAbsent(sid, k -> new ArrayList<>()).add(pure);
            }
        }

        // step 6 — sections 注入 questions（不存在的 section 给空 list）
        for (PaperSectionVo sec : sections) {
            sec.setQuestions(bySection.getOrDefault(sec.getSectionId(), Collections.emptyList()));
        }

        header.setSections(sections);
        return header;
    }

    // ---------------- 复用 helpers（与 QuestionServiceImpl / PaperSourceServiceImpl 同源） ----------------

    /**
     * 批量按 question_id + source 拉 knowledges 并按 questionId 分组。
     *
     * <p>跟 {@code QuestionServiceImpl#loadKnowledgesByQuestionIds} 同源。
     */
    private Map<Long, List<QuestionKnowledgeVo>> loadKnowledgesByQuestionIds(Collection<Long> questionIds,
                                                                              String source) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<QuestionKnowledgeVo> all =
            bizQuestionKnowledgeMapper.selectByQuestionIdsAndSource(questionIds, source);
        if (all == null || all.isEmpty()) {
            return Collections.emptyMap();
        }
        return all.stream().collect(Collectors.groupingBy(
            QuestionKnowledgeVo::getQuestionId,
            LinkedHashMap::new,
            Collectors.toList()));
    }

    /**
     * 批量按 question_id 拉 freeTags 并按 questionId 分组（与 PaperSourceServiceImpl 同源）。
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
