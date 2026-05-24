package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.mapper.BizPaperBasketMapper;
import org.dromara.book.service.IPaperBasketService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 试卷筐 Service 实现 — R 卡段① 对称 {@link QuestionBasketServiceImpl} 复刻。
 *
 * <p>设计要点：
 * <ul>
 *   <li>add 走 {@code INSERT IGNORE}（mapper.xml 定制 SQL） — 复合主键 (user_id, paper_id) 防重</li>
 *   <li>cancel / empty 走物理 DELETE 封装在本 Service 不裸调 mapper（Iron law §1.6）</li>
 *   <li>queryBasket 不分页（misikt 真实行为，FE TS 类型 {@code PaperBasketItem[]}）</li>
 *   <li>不回填 questionKnowledges/freeTags — 卷列表仅卷头字段（PaperListItemVo），跟试题筐
 *       回填题级 knowledges 不同（卷头无须）</li>
 *   <li>JOIN biz_paper 在 mapper.xml 内完成 — Service 仅做参数校验 + 调用</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class PaperBasketServiceImpl implements IPaperBasketService {

    private final BizPaperBasketMapper bizPaperBasketMapper;

    @Override
    public void addBasket(Long userId, Long paperId) {
        if (userId == null || paperId == null) {
            return;
        }
        // INSERT IGNORE — 命中复合主键则忽略；命中也返 R.ok()（misikt 真实行为：重复加不报错）
        bizPaperBasketMapper.insertIgnore(userId, paperId);
    }

    @Override
    public void cancel(Long userId, Long paperId) {
        if (userId == null || paperId == null) {
            return;
        }
        bizPaperBasketMapper.deleteByUserAndPaper(userId, paperId);
    }

    @Override
    public List<PaperListItemVo> queryBasket(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<PaperListItemVo> records = bizPaperBasketMapper.selectBasketPapersByUser(userId);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records;
    }

    @Override
    public void empty(Long userId) {
        if (userId == null) {
            return;
        }
        bizPaperBasketMapper.deleteAllByUser(userId);
    }

    @Override
    public long basketNum(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return bizPaperBasketMapper.countByUser(userId);
    }
}
