package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizQuestionNote;
import org.dromara.book.domain.vo.NoteVo;
import org.dromara.book.mapper.BizQuestionNoteMapper;
import org.dromara.book.service.INoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 题目个人备注 Service 实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li>LambdaQueryWrapper 优先（脚手架自带，避免裸 SQL）— 表很简单不需要 mapper.xml</li>
 *   <li>saveNote 走"先 selectOne 判存在，再 update 或 insert"模式（upsert）—
 *       与 FavoriteServiceImpl.toggle 同模式，区别在 toggle 是删/插翻转，这里是改/插更新</li>
 *   <li>uk_user_question 唯一键保底防并发双插；正常单用户单 tab 操作不会撞</li>
 *   <li>saveNote 加 {@code @Transactional} — selectOne + update/insert 两步在同事务，防中间态</li>
 *   <li>update_time 走 MP {@code @TableField(fill = INSERT_UPDATE)} 自动填，与 DB
 *       {@code ON UPDATE CURRENT_TIMESTAMP} 双保险</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements INoteService {

    private final BizQuestionNoteMapper bizQuestionNoteMapper;

    @Override
    public NoteVo getNote(Long userId, Long questionId) {
        if (userId == null || questionId == null) {
            return null;
        }
        BizQuestionNote existing = bizQuestionNoteMapper.selectOne(
            new LambdaQueryWrapper<BizQuestionNote>()
                .eq(BizQuestionNote::getUserId, userId)
                .eq(BizQuestionNote::getQuestionId, questionId)
        );
        if (existing == null) {
            // 契约：未写过笔记 → null（advice 自动转 {code:1, message:"成功", response: null}）
            return null;
        }
        return new NoteVo(existing.getContent(), existing.getUpdateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveNote(Long userId, Long questionId, String content) {
        if (userId == null || questionId == null) {
            // 入参不合法时静默返回；不抛错（防止 FE 弹错）
            return;
        }
        // content null 容忍 — 兜底空串避免 DB NOT NULL 撞
        String safeContent = content != null ? content : "";

        BizQuestionNote existing = bizQuestionNoteMapper.selectOne(
            new LambdaQueryWrapper<BizQuestionNote>()
                .eq(BizQuestionNote::getUserId, userId)
                .eq(BizQuestionNote::getQuestionId, questionId)
        );
        if (existing != null) {
            // 已存在 → UPDATE content + update_time（update_time 走 MP fill 自动覆盖）
            existing.setContent(safeContent);
            existing.setUpdateTime(new Date());
            bizQuestionNoteMapper.updateById(existing);
            return;
        }
        // 不存在 → INSERT（create_time / update_time 走 MP fill 自动填）
        BizQuestionNote entity = new BizQuestionNote();
        entity.setUserId(userId);
        entity.setQuestionId(questionId);
        entity.setContent(safeContent);
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        bizQuestionNoteMapper.insert(entity);
    }
}
