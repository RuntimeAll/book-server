package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizQuestionFavorite;
import org.dromara.book.domain.vo.FavoriteToggleVo;
import org.dromara.book.domain.vo.FreeTagVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.mapper.BizQuestionFavoriteMapper;
import org.dromara.book.mapper.BizQuestionFreeTagMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.service.IFavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目收藏 Service 实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li>LambdaQueryWrapper 优先（脚手架自带，避免裸 SQL）— 表很简单不需要 mapper.xml</li>
 *   <li>toggle 走"先 selectOne 判存在，再 insert 或 delete"模式 — biz_question_basket 走的
 *       INSERT IGNORE 模式是因为 basket 只有"加"语义不需要状态翻转；favorite 是 toggle 必须先判</li>
 *   <li>uk_user_question 唯一键保底防并发双插；正常单用户单 tab 操作不会撞</li>
 *   <li>toggle 加 {@code @Transactional} — selectOne + delete/insert 两步在同事务，防中间态</li>
 *   <li>cancel 走物理 DELETE — Iron law §1.6 用户态数据可物理删，与 basket cancel 一致</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements IFavoriteService {

    private final BizQuestionFavoriteMapper bizQuestionFavoriteMapper;
    private final BizQuestionMapper bizQuestionMapper;
    private final BizQuestionFreeTagMapper bizQuestionFreeTagMapper;

    /** misikt 默认每页 10，pageNum 兜底 1（与题库 page 对齐） */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_NUM = 1;

    @Override
    public boolean isFavorite(Long userId, Long questionId) {
        if (userId == null || questionId == null) {
            return false;
        }
        Long count = bizQuestionFavoriteMapper.selectCount(
            new LambdaQueryWrapper<BizQuestionFavorite>()
                .eq(BizQuestionFavorite::getUserId, userId)
                .eq(BizQuestionFavorite::getQuestionId, questionId)
        );
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteToggleVo toggle(Long userId, Long questionId, Long folderId) {
        if (userId == null || questionId == null) {
            // 入参不合法时按"未收藏"返 false；不抛错（防止 misikt FE 弹错）
            return new FavoriteToggleVo(false);
        }
        BizQuestionFavorite existing = bizQuestionFavoriteMapper.selectOne(
            new LambdaQueryWrapper<BizQuestionFavorite>()
                .eq(BizQuestionFavorite::getUserId, userId)
                .eq(BizQuestionFavorite::getQuestionId, questionId)
        );
        if (existing != null) {
            // 已收藏 → 取消（物理删）→ false
            bizQuestionFavoriteMapper.deleteById(existing.getId());
            return new FavoriteToggleVo(false);
        }
        // 未收藏 → 新增 → true
        BizQuestionFavorite entity = new BizQuestionFavorite();
        entity.setUserId(userId);
        entity.setQuestionId(questionId);
        entity.setFolderId(folderId != null ? folderId : 0L);
        entity.setCreateTime(new Date());
        bizQuestionFavoriteMapper.insert(entity);
        return new FavoriteToggleVo(true);
    }

    @Override
    public void cancel(Long userId, Long questionId) {
        if (userId == null || questionId == null) {
            return;
        }
        // delete 0 / 1 行都成功 — 幂等语义（L-4）
        bizQuestionFavoriteMapper.delete(
            new LambdaQueryWrapper<BizQuestionFavorite>()
                .eq(BizQuestionFavorite::getUserId, userId)
                .eq(BizQuestionFavorite::getQuestionId, questionId)
        );
    }

    /**
     * PRD-A-005 T5 — 分页查当前用户收藏题。
     *
     * <p>复用题库现成的 {@link QuestionItemVo} + {@link MisiktPageVo}（FE 直接复用 QuestionCard）。
     * mapper INNER JOIN biz_question_favorite 限当前 userId（防越权），folderId 非空再过滤。
     * is_favorite 恒 1（已是收藏列表）；freeTags 二次批量回填，与题库 page 同款（免 N+1）。
     */
    @Override
    public MisiktPageVo<QuestionItemVo> page(Long userId, Integer pageNum, Integer pageSize, Long folderId) {
        if (userId == null) {
            // 防越权兜底：理论上 @SaCheckLogin 已拦，此处返空页不抛错
            return MisiktPageVo.of(new Page<>(DEFAULT_PAGE_NUM, DEFAULT_PAGE_SIZE));
        }
        int pn = (pageNum == null || pageNum <= 0) ? DEFAULT_PAGE_NUM : pageNum;
        int ps = (pageSize == null || pageSize <= 0) ? DEFAULT_PAGE_SIZE : pageSize;

        Page<QuestionItemVo> mpPage = new Page<>(pn, ps);
        IPage<QuestionItemVo> result = bizQuestionMapper.selectFavoritePage(mpPage, userId, folderId);

        // 回填 freeTags（X 卡段② 结构化标签，FE 卡片渲染用；knowledges 收藏卡片非必须不回填）
        List<QuestionItemVo> records = result.getRecords();
        if (records != null && !records.isEmpty()) {
            List<Long> ids = records.stream().map(QuestionItemVo::getId).collect(Collectors.toList());
            Map<Long, List<FreeTagVo>> ftMap = loadFreeTagsByQuestionIds(ids);
            for (QuestionItemVo vo : records) {
                vo.setFreeTags(ftMap.getOrDefault(vo.getId(), Collections.emptyList()));
            }
        }
        return MisiktPageVo.of(result);
    }

    /**
     * 批量按 question_id 拉 freeTags 并按 questionId 分组（与 QuestionServiceImpl 同款，X 卡段②）。
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
