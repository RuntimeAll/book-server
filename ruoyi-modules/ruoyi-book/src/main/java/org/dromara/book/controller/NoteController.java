package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.NoteVo;
import org.dromara.book.service.INoteService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 题目个人备注 Controller（PRD §3.1 B-4/B-5 共 2 端点）。
 *
 * <p>路径前缀 {@code /teacher/qd/note/{id}}，命中 {@link org.dromara.web.advice.MisiktEnvelopeAdvice}
 * 自动包 {@code {code:1, message:"成功", response:<T>}} envelope — Controller 直接返业务对象 / null。
 *
 * <p>2 端点：
 * <ul>
 *   <li>GET  /teacher/qd/note/{id} — 取当前用户对该题的备注 → {@link NoteVo} 或 {@code null}</li>
 *   <li>POST /teacher/qd/note/{id} — upsert 备注 body {@code {content}} → {@code null}</li>
 * </ul>
 *
 * <p>鉴权：{@code @SaCheckLogin}，user_id 走 {@code LoginHelper.getUserId()}。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/qd/note")
@RequiredArgsConstructor
public class NoteController {

    private final INoteService noteService;

    /**
     * GET /teacher/qd/note/{id} — 取当前用户对该题的备注。
     *
     * @param id 题目 id
     * @return {@link NoteVo}（content + updateTime），未写过笔记返 {@code null}
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public NoteVo getNote(@PathVariable("id") Long id) {
        Long userId = LoginHelper.getUserId();
        return noteService.getNote(userId, id);
    }

    /**
     * POST /teacher/qd/note/{id} — upsert 备注（存在则更新，不存在则插入）。
     *
     * <p>body 形如 {@code {"content":"我的笔记"}}。FE 也可能不传 body —
     * 用 {@code @RequestBody(required=false) Map} 双兼容（content 兜底空串）。
     *
     * @param id   题目 id
     * @param body 可空，含 {@code content}
     * @return null（advice 自动转 {@code {code:1, message:"成功", response:null}}）
     */
    @SaCheckLogin
    @PostMapping("/{id}")
    public Object saveNote(@PathVariable("id") Long id,
                           @RequestBody(required = false) Map<String, Object> body) {
        Long userId = LoginHelper.getUserId();
        String content = parseContent(body);
        noteService.saveNote(userId, id, content);
        return null;
    }

    /**
     * 从 body 解 content — 兼容 String / null / key 缺失 / 非 String 4 种场景。
     */
    private String parseContent(Map<String, Object> body) {
        if (body == null) {
            return "";
        }
        Object raw = body.get("content");
        if (raw == null) {
            return "";
        }
        return raw.toString();
    }
}
