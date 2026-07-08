package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.bo.AutoGenerateBo;
import org.dromara.book.domain.bo.CreateExamPaperBo;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.enums.BizQuestionType;
import org.dromara.book.domain.vo.AutoGeneratePaperVo;
import org.dromara.book.domain.vo.CreateExamPaperVo;
import org.dromara.book.domain.vo.ExamSectionVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.service.IAutoPaperService;
import org.dromara.book.service.IPaperLibraryService;
import org.dromara.book.service.IQuestionService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PRD-C-001 — AI 组卷确定性后端 Service 实现。
 *
 * <p>分层架构后段：零 LLM，纯代码确定性逻辑。复用 {@link IQuestionService#page} 现有题库查询能力
 * （含 biz_question_knowledge.knowledge_id 前缀递归关联查询 + status='1' 过滤 + knowledges/freeTags
 * 回填），不重造查询。
 *
 * <p>fallback 三层（每个 outline 项独立走）：
 * <ol>
 *   <li>L1 精确：subjectId × questionType × difficult，够 count → 取前 count（去重后）。</li>
 *   <li>L2 放宽难度：L1 不够 → 去掉 difficult 再查（仍同 subjectId + questionType）补充。</li>
 *   <li>L3 缺口：L2 仍不够 → 记一条 gap（want/got/reason），🔴 不跨章节硬凑，宁可少给。</li>
 * </ol>
 *
 * @author backend-dev
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPaperServiceImpl implements IAutoPaperService {

    private final IQuestionService questionService;
    private final IPaperLibraryService paperLibraryService;

    /** book-ui 前端基址，用于拼卷详情深链；prod 改 application.yml book.front-base-url。 */
    @Value("${book.front-base-url:http://localhost:5173}")
    private String frontBaseUrl;

    /** misikt 真实题型顺序：1=选择 → 4=填空 → 5=解答 → 6=作图（PRD-C-204 B1 追加 6，1/4/5 原行为不变） */
    private static final int[] TYPE_ORDER = {1, 4, 5, 6};
    /** 大题序号（一、二、三…），按 TYPE_ORDER 位次取。 */
    private static final String[] CN_NUM = {"一", "二", "三", "四", "五", "六", "七", "八"};
    /**
     * 题型码 → 大题标题（如 5→"三、解答题"）。题型名取自 {@link BizQuestionType}（SSOT=字典
     * biz_question_type），序号按 TYPE_ORDER 位次，不再硬编码「简答题」等魔法值。
     */
    private static final Map<Integer, String> TYPE_TITLE;
    static {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            m.put(TYPE_ORDER[i], CN_NUM[i] + "、" + BizQuestionType.labelOf(TYPE_ORDER[i]));
        }
        TYPE_TITLE = Collections.unmodifiableMap(m);
    }

    /** 候选拉取放大系数 — 抵消全局去重损耗（page 返回可能含已被其他 outline 项选走的题） */
    private static final int CANDIDATE_FACTOR = 5;
    private static final int CANDIDATE_MIN = 50;
    /** 单页上限兜底，防超大 count 拖垮查询 */
    private static final int CANDIDATE_MAX = 500;

    @Override
    public AutoGeneratePaperVo autoGenerate(AutoGenerateBo bo) {
        if (bo == null || bo.getOutline() == null || bo.getOutline().isEmpty()) {
            throw new ServiceException("组卷大纲 outline 不能为空");
        }
        // PRD-B-101 G7：lessonId 与 slotSeq 必须同现（与 /teacher/exam/paper/create 同语义）。
        boolean bindSlot = bo.getLessonId() != null;
        if (bindSlot != (bo.getSlotSeq() != null)) {
            throw new ServiceException("lessonId 与 slotSeq 必须同时提供（备课语境绑卷位）", 400);
        }
        // 绑卷位必须真的落库：save=true + teacherId 有效，否则无卷可绑=契约误用，显式 400 不静默。
        if (bindSlot && (!Boolean.TRUE.equals(bo.getSave()) || bo.getTeacherId() == null)) {
            throw new ServiceException("绑卷位（lessonId+slotSeq）要求 save=true 且 teacherId 有效", 400);
        }
        boolean dedup = bo.getDedup() == null || bo.getDedup();

        // 全局已选题 id（dedup=true 时跨 outline 项去重）
        Set<Long> selectedIds = new HashSet<>();
        // 按题型聚合选中的题（保持 outline 遍历顺序）
        Map<Integer, List<QuestionItemVo>> byType = new LinkedHashMap<>();
        // coverage：subjectId(name) -> 命中题数
        Map<String, Integer> coverage = new LinkedHashMap<>();
        List<AutoGeneratePaperVo.Gap> gaps = new ArrayList<>();

        int fallbackRelaxCount = 0;

        for (AutoGenerateBo.OutlineItem item : bo.getOutline()) {
            if (item == null || item.getSubjectId() == null || item.getSubjectId().isEmpty()
                || item.getQuestionType() == null) {
                continue;
            }
            int want = item.getCount() == null || item.getCount() <= 0 ? 0 : item.getCount();
            if (want == 0) {
                continue;
            }

            List<QuestionItemVo> picked = new ArrayList<>(want);
            // 项内已选 id（L1/L2 之间必须自去重，避免 L2 去 difficult 重查把 L1 的题再带回；
            // 与全局 selectedIds 解耦 — dedup=false 时全局可重复但项内仍不重复）
            Set<Long> pickedIds = new HashSet<>();

            // ── L1 精确：subjectId × questionType × difficult ──
            pick(item.getSubjectId(), item.getQuestionType(), item.getDifficult(),
                want, dedup, selectedIds, pickedIds, picked);

            boolean relaxed = false;
            // ── L2 放宽难度：仍不够且本项原本指定了 difficult ──
            if (picked.size() < want && item.getDifficult() != null) {
                relaxed = true;
                fallbackRelaxCount++;
                pick(item.getSubjectId(), item.getQuestionType(), null,
                    want, dedup, selectedIds, pickedIds, picked);
            }

            // ── L3 缺口：仍不够 → 记 gap，不跨章节硬凑 ──
            if (picked.size() < want) {
                AutoGeneratePaperVo.Gap gap = new AutoGeneratePaperVo.Gap();
                gap.setSubjectId(item.getSubjectId());
                gap.setQuestionType(item.getQuestionType());
                gap.setDifficult(relaxed ? null : item.getDifficult());
                gap.setWant(want);
                gap.setGot(picked.size());
                gap.setReason(relaxed
                    ? "该章节该题型放宽难度后仍仅 " + picked.size() + " 题，不足 " + want + " 题（不跨章节硬凑）"
                    : "该章节该题型该难度仅 " + picked.size() + " 题，不足 " + want + " 题（不跨章节硬凑）");
                gaps.add(gap);
            }

            // 聚合到题型分组 + coverage
            if (!picked.isEmpty()) {
                byType.computeIfAbsent(item.getQuestionType(), k -> new ArrayList<>()).addAll(picked);
                String covKey = item.getSubjectName() == null || item.getSubjectName().isEmpty()
                    ? item.getSubjectId()
                    : item.getSubjectId() + "(" + item.getSubjectName() + ")";
                coverage.merge(covKey, picked.size(), Integer::sum);
            }
        }

        // 按 misikt 题型顺序 1→4→5 组 sections
        List<ExamSectionVo> sections = new ArrayList<>(TYPE_ORDER.length);
        int totalCount = 0;
        for (int type : TYPE_ORDER) {
            List<QuestionItemVo> qs = byType.get(type);
            if (qs == null || qs.isEmpty()) {
                continue;
            }
            ExamSectionVo section = new ExamSectionVo();
            section.setQuestionType(type);
            section.setTitle(TYPE_TITLE.get(type));
            section.setQuestions(qs);
            sections.add(section);
            totalCount += qs.size();
        }

        AutoGeneratePaperVo.Paper paper = new AutoGeneratePaperVo.Paper();
        paper.setTitle(bo.getTitle() == null || bo.getTitle().isEmpty() ? "AI 智能组卷" : bo.getTitle());
        paper.setTotalCount(totalCount);
        paper.setSections(sections);

        AutoGeneratePaperVo vo = new AutoGeneratePaperVo();
        vo.setPaper(paper);
        vo.setCoverage(coverage);
        vo.setGaps(gaps);
        vo.setNotes(bo.getNotesFromLLM());
        vo.setTips(buildTips(totalCount, dedup, fallbackRelaxCount, gaps.size()));

        // 可选落库：save=true 且 teacherId 有效 → 写进该老师"我的卷库"，回填 paperId + 深链。
        // 收集组装后各 section 的真实 biz_question.id（保持试卷内顺序）。
        if (Boolean.TRUE.equals(bo.getSave()) && bo.getTeacherId() != null && totalCount > 0) {
            List<Long> questionIds = new ArrayList<>(totalCount);
            for (ExamSectionVo sec : sections) {
                if (sec.getQuestions() == null) {
                    continue;
                }
                for (QuestionItemVo q : sec.getQuestions()) {
                    if (q.getId() != null) {
                        questionIds.add(q.getId());
                    }
                }
            }
            if (!questionIds.isEmpty()) {
                CreateExamPaperBo createBo = new CreateExamPaperBo();
                createBo.setName(paper.getTitle());
                createBo.setQuestionIds(questionIds);
                // PRD-B-101 G7：透传备课语境 → createExamPaperForTeacher 统一处理
                // paper_kind='2' + 事务内绑卷位（slot 不存在 → 400 整体回滚），逻辑单一事实源不复制。
                createBo.setLessonId(bo.getLessonId());
                createBo.setSlotSeq(bo.getSlotSeq());
                CreateExamPaperVo created = paperLibraryService.createExamPaperForTeacher(createBo, bo.getTeacherId());
                vo.setPaperId(created.getPaperId());
                // 🔴 book-ui 用 hash 路由（createWebHashHistory），深链必须带 /#，
                //    否则浏览器把 /papers/source/{id} 当真实路径请求 → 命中不到路由 404。
                vo.setPaperUrl(frontBaseUrl + "/#/papers/source/" + created.getPaperId());
                log.info("[AI组卷] 已落库 paperId={} teacherId={} 题数={}", created.getPaperId(), bo.getTeacherId(), questionIds.size());
            }
        }
        // 绑卷位被请求但组卷无题未落库 → 显式 400（不静默吞掉绑定诉求）
        if (bindSlot && vo.getPaperId() == null) {
            throw new ServiceException("组卷结果为空未落库，无法绑卷位（检查 outline 是否命中题库）", 400);
        }
        return vo;
    }

    /**
     * 从 page 接口拉候选题，去重后补进 picked（直到够 want 或候选耗尽）。
     *
     * <p>复用 {@link IQuestionService#page}：内部走 biz_question_knowledge.knowledge_id 前缀递归
     * 关联查询 + status='1' 过滤，与 /teacher/question/page 完全一致的查询能力。
     */
    private void pick(String subjectId, Integer questionType, Integer difficult,
                      int want, boolean dedup, Set<Long> selectedIds, Set<Long> pickedIds,
                      List<QuestionItemVo> picked) {
        if (picked.size() >= want) {
            return;
        }
        int candidateSize = Math.min(CANDIDATE_MAX, Math.max(CANDIDATE_MIN, want * CANDIDATE_FACTOR));

        QuestionPageBo q = new QuestionPageBo();
        q.setPageIndex(1);
        q.setPageSize(candidateSize);
        q.setSubjectId(subjectId);
        q.setQuestionType(questionType);
        q.setDifficult(difficult);

        List<QuestionItemVo> candidates = questionService.page(q).getList();
        if (candidates == null) {
            return;
        }
        for (QuestionItemVo c : candidates) {
            if (picked.size() >= want) {
                break;
            }
            if (c == null || c.getId() == null) {
                continue;
            }
            // 项内自去重（L1/L2 之间必须，永远生效）
            if (pickedIds.contains(c.getId())) {
                continue;
            }
            // 全局去重（仅 dedup=true 时跨 outline 项生效）
            if (dedup && selectedIds.contains(c.getId())) {
                continue;
            }
            picked.add(c);
            pickedIds.add(c.getId());
            selectedIds.add(c.getId());
        }
    }

    /**
     * 规则文案。
     */
    private String buildTips(int totalCount, boolean dedup, int relaxCount, int gapCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("已从真实题库确定性选出 ").append(totalCount).append(" 道题");
        sb.append(dedup ? "（全局去重已开启）" : "（未开启去重）");
        sb.append("。");
        if (relaxCount > 0) {
            sb.append("其中 ").append(relaxCount).append(" 项因精确难度题量不足，已放宽难度补足。");
        }
        if (gapCount > 0) {
            sb.append("仍有 ").append(gapCount).append(" 项存在缺口（详见 gaps），按规则宁可少给不跨章节硬凑。");
        }
        if (relaxCount == 0 && gapCount == 0) {
            sb.append("各项均按精确难度足量命中。");
        }
        return sb.toString();
    }
}
