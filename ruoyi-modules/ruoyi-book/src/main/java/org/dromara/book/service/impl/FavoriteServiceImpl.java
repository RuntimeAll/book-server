package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizQuestionFavorite;
import org.dromara.book.domain.vo.FavoriteToggleVo;
import org.dromara.book.mapper.BizQuestionFavoriteMapper;
import org.dromara.book.service.IFavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

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
}
