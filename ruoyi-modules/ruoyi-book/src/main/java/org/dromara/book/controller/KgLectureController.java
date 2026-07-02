package org.dromara.book.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.book.service.IKgLectureService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 讲义浏览接口（PRD-C-207）—— 讲义 = 挂 KG 节点的原子片段，按前缀树序汇聚成一份可只读渲染的 doc。
 *
 * <p>前缀 {@code /teacher/kg/**}，命中 MisiktEnvelopeAdvice 包 {@code {code:1, message, response}}。
 * example 节点仍走既有 {@code GET /teacher/kg/questions?qids=} 拉题面（本控制器不重复）。
 * <ul>
 *   <li>GET /teacher/kg/lecture?subjectId=901001002002&bookId=CC7S — 取某节点完整讲义（片段汇聚）</li>
 *   <li>GET /teacher/kg/lecture-catalog?bookId=CC7S — 册内每课时的讲义源（左树灰置 + 来源切换器）</li>
 * </ul>
 *
 * @author codeplace-C PRD-C-207
 */
@RestController
@RequestMapping("/teacher/kg")
@RequiredArgsConstructor
public class KgLectureController {

    private final IKgLectureService kgLectureService;

    @GetMapping("/lecture")
    public R<Map<String, Object>> lecture(@RequestParam("subjectId") String subjectId,
                                          @RequestParam(value = "bookId", required = false) String bookId) {
        return R.ok(kgLectureService.getLecture(subjectId, bookId));
    }

    @GetMapping("/lecture-catalog")
    public R<Map<String, Object>> catalog(@RequestParam(value = "bookId", required = false) String bookId) {
        return R.ok(kgLectureService.getCatalog(bookId));
    }
}
