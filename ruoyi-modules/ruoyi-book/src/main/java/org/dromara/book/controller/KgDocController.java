package org.dromara.book.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.book.service.IKgDocService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 课件文档接口（PRD-C-205）—— 课件 = 一课时一份 Umo/Tiptap 文档 JSON。
 *
 * <p>前缀 {@code /teacher/kg/**}，命中 MisiktEnvelopeAdvice 包 {@code {code:1, message, response}}。
 * <ul>
 *   <li>GET  /teacher/kg/doc/{courseId}?bookId=CC7S — 读课件文档（前台只读渲染 + 后台编辑加载）</li>
 *   <li>POST /teacher/kg/doc/{courseId} — 保存课件文档 JSON（超管/备课后台）</li>
 *   <li>GET  /teacher/kg/questions?qids=1,2,3 — example 节点按 qid 批量取题面/答案/详解</li>
 * </ul>
 *
 * @author codeplace-C PRD-C-205
 */
@RestController
@RequestMapping("/teacher/kg")
@RequiredArgsConstructor
public class KgDocController {

    private final IKgDocService kgDocService;

    @GetMapping("/doc/{courseId}")
    public R<Map<String, Object>> getDoc(@PathVariable("courseId") String courseId,
                                         @RequestParam(value = "bookId", required = false) String bookId) {
        return R.ok(kgDocService.getDoc(courseId, bookId));
    }

    @PostMapping("/doc/{courseId}")
    public R<Void> saveDoc(@PathVariable("courseId") String courseId,
                           @RequestBody Map<String, Object> body) {
        String bookId = str(body.get("bookId"));
        String title = str(body.get("title"));
        // docJson 前端传对象或字符串；统一存字符串
        Object dj = body.get("docJson");
        String docJson = dj == null ? null
            : (dj instanceof String ? (String) dj : org.dromara.common.json.utils.JsonUtils.toJsonString(dj));
        int n = kgDocService.saveDoc(courseId, bookId, title, docJson);
        return n > 0 ? R.ok() : R.fail("保存失败");
    }

    @GetMapping("/questions")
    public R<List<Map<String, Object>>> questions(@RequestParam("qids") String qids) {
        return R.ok(kgDocService.getQuestions(qids));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
