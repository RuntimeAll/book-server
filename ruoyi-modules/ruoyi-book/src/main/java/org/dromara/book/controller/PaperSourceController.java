package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.PaperSourceVo;
import org.dromara.book.service.IPaperSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 原卷预览 Controller（PRD §3.1 B-10 — GET /teacher/paper/source/{id}）。
 *
 * <p>路径前缀 {@code /teacher/paper/source} 命中 {@code MisiktEnvelopeAdvice} 自动包
 * envelope — Controller 直接返业务对象 / null。
 *
 * <p>契约：
 * <pre>
 *   GET /teacher/paper/source/{id}
 *   → response: {paperId, paperName, examYear?, questions: [{...题字段, sort}]}
 *
 *   边界：
 *     paper 不存在 / status≠'1' → response: null
 *     paper 存在但 0 题 → response: {paperId, paperName, examYear, questions: []}
 * </pre>
 *
 * @author backend-dev
 */
@RestController
@RequiredArgsConstructor
public class PaperSourceController {

    private final IPaperSourceService paperSourceService;

    /**
     * GET /teacher/paper/source/{id} — 原卷预览。
     *
     * @param id 试卷 id
     * @return 卷头 + 卷下所有题；不存在 / 草稿 / 软删 → null（envelope 自动包）
     */
    @SaCheckLogin
    @GetMapping("/teacher/paper/source/{id}")
    public PaperSourceVo source(@PathVariable("id") Long id) {
        return paperSourceService.getPaperSource(id);
    }
}
