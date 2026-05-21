package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.vo.ExamDataVo;
import org.dromara.book.domain.vo.ExamSectionVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.service.IQuestionBasketService;
import org.dromara.book.service.IQuestionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目 Service 实现。
 *
 * <p>page / select 两端点公用 knowledges 回填策略：
 * <ol>
 *   <li>主表 SQL 查 list（page）或单条（select），不带 knowledges 关联</li>
 *   <li>二次查 biz_question_knowledge LEFT JOIN biz_subject 按 questionId 集合批量拿</li>
 *   <li>按 source='U' / 'S' 分组回填到对应字段</li>
 * </ol>
 *
 * <p>避免单条 list × N 次查（N+1 问题），且 mapper.xml 主表 SQL 结构清晰。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements IQuestionService {

    private final BizQuestionMapper bizQuestionMapper;
    private final BizQuestionKnowledgeMapper bizQuestionKnowledgeMapper;
    private final IQuestionBasketService questionBasketService;

    /** misikt 默认每页 10，pageIndex 兜底 1 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_INDEX = 1;

    @Override
    public MisiktPageVo<QuestionItemVo> page(QuestionPageBo bo) {
        int pageIndex = bo.getPageIndex() == null || bo.getPageIndex() <= 0
            ? DEFAULT_PAGE_INDEX : bo.getPageIndex();
        int pageSize = bo.getPageSize() == null || bo.getPageSize() <= 0
            ? DEFAULT_PAGE_SIZE : bo.getPageSize();

        QueryWrapper<BizQuestion> wrapper = buildPageWrapper(bo);

        Page<QuestionItemVo> mpPage = new Page<>(pageIndex, pageSize);
        IPage<QuestionItemVo> result = bizQuestionMapper.selectQuestionPage(mpPage, wrapper);

        // 回填 questionKnowledges（source='U'）
        List<QuestionItemVo> records = result.getRecords();
        if (records != null && !records.isEmpty()) {
            List<Long> ids = records.stream().map(QuestionItemVo::getId).collect(Collectors.toList());
            Map<Long, List<QuestionKnowledgeVo>> uMap = loadKnowledgesByQuestionIds(ids, "U");
            for (QuestionItemVo vo : records) {
                vo.setQuestionKnowledges(uMap.getOrDefault(vo.getId(), Collections.emptyList()));
            }
        }

        return MisiktPageVo.of(result);
    }

    @Override
    public QuestionDetailVo selectById(Long id) {
        if (id == null) {
            return null;
        }
        QuestionDetailVo vo = bizQuestionMapper.selectQuestionDetailById(id);
        if (vo == null) {
            return null;
        }
        // 回填 questionKnowledges + questionStdKnowledges
        List<Long> ids = Collections.singletonList(id);
        vo.setQuestionKnowledges(loadKnowledgesByQuestionIds(ids, "U").getOrDefault(id, new ArrayList<>()));
        vo.setQuestionStdKnowledges(loadKnowledgesByQuestionIds(ids, "S").getOrDefault(id, new ArrayList<>()));
        return vo;
    }


    /**
     * 组卷草稿 — section 顺序固定 1=选择 → 4=填空 → 5=简答（misikt 真实行为）。
     *
     * <p>实现策略：复用 {@link IQuestionBasketService#queryBasket}（已带 status='1' 过滤
     * + add_time 倒序 + questionKnowledges 回填），按 questionType 分组装 sections。
     */
    @Override
    public ExamDataVo genExamData(Long userId) {
        ExamDataVo vo = new ExamDataVo();
        if (userId == null) {
            vo.setSections(Collections.emptyList());
            return vo;
        }
        List<QuestionItemVo> basket = questionBasketService.queryBasket(userId);
        if (basket == null || basket.isEmpty()) {
            vo.setSections(Collections.emptyList());
            return vo;
        }

        // 按 questionType 分组（保留 add_time 倒序 — basket 已排）
        Map<Integer, List<QuestionItemVo>> byType = new LinkedHashMap<>();
        for (QuestionItemVo q : basket) {
            if (q.getQuestionType() == null) {
                continue;
            }
            byType.computeIfAbsent(q.getQuestionType(), k -> new ArrayList<>()).add(q);
        }

        // 按 misikt 真实题型顺序 1 → 4 → 5 生成 sections；不存在的题型跳过
        List<ExamSectionVo> sections = new ArrayList<>(3);
        addSectionIfPresent(sections, byType, 1, "一、选择题");
        addSectionIfPresent(sections, byType, 4, "二、填空题");
        addSectionIfPresent(sections, byType, 5, "三、简答题");

        vo.setSections(sections);
        return vo;
    }

    /**
     * 工具方法：题型有题才加 section。
     */
    private void addSectionIfPresent(List<ExamSectionVo> sections,
                                     Map<Integer, List<QuestionItemVo>> byType,
                                     Integer questionType,
                                     String title) {
        List<QuestionItemVo> qs = byType.get(questionType);
        if (qs == null || qs.isEmpty()) {
            return;
        }
        ExamSectionVo section = new ExamSectionVo();
        section.setTitle(title);
        section.setQuestionType(questionType);
        section.setQuestions(qs);
        sections.add(section);
    }

    /**
     * 构造 page 查询 WHERE 条件。
     *
     * <p>条件构造铁则：
     * <ul>
     *   <li>{@code status='1'} 硬编码（V0.1 只返已发布题；草稿 / 软删隐式过滤）</li>
     *   <li>subjectId 空或 "0" 不过滤</li>
     *   <li>{@code notUsedQuestion=1} 才加 NOT IN biz_paper_question 子查询</li>
     *   <li>{@code notTaskQuestion=1} 加 NOT IN biz_task_question 子查询（V0.1 表暂无数据，结果等同 0）</li>
     * </ul>
     */
    private QueryWrapper<BizQuestion> buildPageWrapper(QuestionPageBo bo) {
        QueryWrapper<BizQuestion> w = new QueryWrapper<>();
        w.eq("status", "1");

        if (bo.getSubjectId() != null && !bo.getSubjectId().isEmpty() && !"0".equals(bo.getSubjectId())) {
            // ⚠️ BUG-2 真修（2026-05-21）：题↔章节关联走 biz_question_knowledge.knowledge_id，
            // 不要走 biz_question.subject_id（那是卷库分类编码，跟 biz_subject id 体系不一致）。
            // 见 数据建模/07-补充资料/W-6-章节树数据复刻方案-2026-05-21.md §4.1。
            //
            // SQL 注入防护：biz_subject.id 是数字串（4-15 位），强校验 ^\d+$ 拒非法输入。
            String sid = bo.getSubjectId();
            if (!sid.matches("^\\d+$")) {
                // 非法 subjectId 直接返空集（不报错，misikt 真站也吞）
                w.apply("1=0");
            } else {
                w.inSql("id",
                    "SELECT DISTINCT question_id FROM biz_question_knowledge "
                        + "WHERE knowledge_id LIKE '" + sid + "%'");
            }
        }
        if (bo.getQuestionType() != null) {
            w.eq("question_type", bo.getQuestionType());
        }
        if (bo.getDifficult() != null) {
            w.eq("difficult", bo.getDifficult());
        }
        if (bo.getKeyWord() != null && !bo.getKeyWord().isEmpty()) {
            // V0.1 LIKE 兜底（ngram fulltext 未配 my.cnf）
            w.like("stem_text", bo.getKeyWord());
        }
        if (bo.getNotUsedQuestion() != null && bo.getNotUsedQuestion() == 1) {
            w.notInSql("id", "SELECT question_id FROM biz_paper_question");
        }
        // notTaskQuestion=1：V0.1 schema 无 biz_task_question 表（M6 才上），
        // 入参收下但不施加过滤 — 等同 0=不限。撞 M6 起卡时再补 SQL。
        // if (bo.getNotTaskQuestion() != null && bo.getNotTaskQuestion() == 1) { ... }
        w.orderByDesc("create_time").orderByDesc("id");
        return w;
    }

    /**
     * 批量按 question_id + source 拉 knowledges 并按 questionId 分组。
     *
     * @param questionIds 题目 ID 集合（空集合返空 Map）
     * @param source      'U' 或 'S'
     */
    private Map<Long, List<QuestionKnowledgeVo>> loadKnowledgesByQuestionIds(Collection<Long> questionIds,
                                                                              String source) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<QuestionKnowledgeVo> all = bizQuestionKnowledgeMapper.selectByQuestionIdsAndSource(questionIds, source);
        if (all == null || all.isEmpty()) {
            return Collections.emptyMap();
        }
        return all.stream().collect(Collectors.groupingBy(QuestionKnowledgeVo::getQuestionId));
    }
}
