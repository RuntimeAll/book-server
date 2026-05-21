package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.book.mapper.BizQuestionBasketMapper;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.service.IQuestionBasketService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 试题筐 Service 实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li>add 走 {@code INSERT IGNORE}（mapper.xml 定制 SQL）— 复合主键 (user_id, question_id) 防重</li>
 *   <li>cancel / empty 走物理 DELETE 封装在本 Service 不裸调 mapper（Iron law §1.6）</li>
 *   <li>queryBasket 不分页（misikt 真实行为，FE TS 类型 BasketItem[]）</li>
 *   <li>questionKnowledges 回填走批量 SQL（复用 page 端点策略 — 同样的 BizQuestionKnowledgeMapper）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class QuestionBasketServiceImpl implements IQuestionBasketService {

    private final BizQuestionBasketMapper bizQuestionBasketMapper;
    private final BizQuestionKnowledgeMapper bizQuestionKnowledgeMapper;

    @Override
    public void addBasket(Long userId, Long questionId) {
        if (userId == null || questionId == null) {
            return;
        }
        // INSERT IGNORE — 命中复合主键则忽略；命中也返 R.ok()（misikt 真实行为：重复加不报错）
        bizQuestionBasketMapper.insertIgnore(userId, questionId);
    }

    @Override
    public void cancel(Long userId, Long questionId) {
        if (userId == null || questionId == null) {
            return;
        }
        bizQuestionBasketMapper.deleteByUserAndQuestion(userId, questionId);
    }

    @Override
    public List<QuestionItemVo> queryBasket(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<QuestionItemVo> records = bizQuestionBasketMapper.selectBasketQuestionsByUser(userId);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        // 回填 questionKnowledges（source='U'）— 复用 page 端点批量策略
        List<Long> ids = records.stream().map(QuestionItemVo::getId).collect(Collectors.toList());
        Map<Long, List<QuestionKnowledgeVo>> uMap = loadKnowledgesByQuestionIds(ids, "U");
        for (QuestionItemVo vo : records) {
            vo.setQuestionKnowledges(uMap.getOrDefault(vo.getId(), Collections.emptyList()));
        }
        return records;
    }

    @Override
    public void empty(Long userId) {
        if (userId == null) {
            return;
        }
        bizQuestionBasketMapper.deleteAllByUser(userId);
    }

    @Override
    public long basketNum(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return bizQuestionBasketMapper.countByUser(userId);
    }

    /**
     * 批量按 question_id + source 拉 knowledges 并按 questionId 分组。
     *
     * <p>跟 {@code QuestionServiceImpl#loadKnowledgesByQuestionIds} 同源实现（共用同一 mapper），
     * V0.1 故意不抽公共类 — 撞第三处用到再抽。
     *
     * @param questionIds 题目 ID 集合
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
