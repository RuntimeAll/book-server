package org.dromara.book.service;

import org.dromara.book.domain.vo.NoteVo;

/**
 * 题目个人备注 Service 接口（PRD §3.1 B-4/B-5 共 2 个端点）。
 *
 * <ul>
 *   <li>GET  /teacher/qd/note/{id} → {@link #getNote}（null 即未写过）</li>
 *   <li>POST /teacher/qd/note/{id} → {@link #saveNote}（upsert：存在 update / 不存在 insert）</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface INoteService {

    /**
     * 取当前用户对该题的备注。
     *
     * @param userId     当前用户 id
     * @param questionId 题目 id
     * @return {@link NoteVo}（含 content + updateTime），未写过笔记返 {@code null}
     */
    NoteVo getNote(Long userId, Long questionId);

    /**
     * upsert 备注（存在则更新 content + update_time，不存在则插入）。
     *
     * @param userId     当前用户 id
     * @param questionId 题目 id
     * @param content    备注内容（非空字符串；调用方应在 Controller 层兜空）
     */
    void saveNote(Long userId, Long questionId, String content);
}
