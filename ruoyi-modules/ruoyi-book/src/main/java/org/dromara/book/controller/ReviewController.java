package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.service.ReviewService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 录题比对审核 Controller（PRD-006 批1，/teacher/review）。命中 MisiktEnvelopeAdvice → {code:1,message,response}。
 *
 * <p>源 PDF ↔ 已录题的页级人工比对：进度、源页渲染、页内题项、页级确认、问题登记 CRUD。
 * 鉴权 = {@link SaCheckLogin} 登录闸，风格对齐 {@link ShelfController}。
 *
 * @author backend-dev (PRD-006 批1)
 */
@RestController
@RequestMapping("/teacher/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 审核进度 → {totalPages, reviewedPages}。 */
    @SaCheckLogin
    @GetMapping("/progress")
    public R<Map<String, Object>> progress(@RequestParam Long bookId) {
        return R.ok(reviewService.progress(bookId));
    }

    /** 源 PDF 第 page 页渲染成 PNG（base64 data URL）。 */
    @SaCheckLogin
    @GetMapping("/source-page")
    public R<Map<String, Object>> sourcePage(@RequestParam Long bookId, @RequestParam Integer page) {
        return R.ok(reviewService.sourcePage(bookId, page));
    }

    /** 某源页的已录题项（question/explain 混排，按 seq）。 */
    @SaCheckLogin
    @GetMapping("/page-items")
    public R<Map<String, Object>> pageItems(@RequestParam Long bookId, @RequestParam Integer page) {
        return R.ok(reviewService.pageItems(bookId, page));
    }

    /** 页级确认。body: {@code {bookId, page}}。 */
    @SaCheckLogin
    @PutMapping("/confirm-page")
    public R<Map<String, Object>> confirmPage(@RequestBody Map<String, Object> body) {
        Long bookId = idOf(get(body, "bookId"));
        Integer page = intOf(get(body, "page"));
        return R.ok(reviewService.confirmPage(bookId, page));
    }

    /** 登记问题。body: {@code {bookId, questionId?, sourcePage, issueType, description}}。返回 {id}。 */
    @SaCheckLogin
    @PostMapping("/issue")
    public R<Map<String, Object>> createIssue(@RequestBody Map<String, Object> body) {
        Long bookId = idOf(get(body, "bookId"));
        Long questionId = idOf(get(body, "questionId"));
        Integer sourcePage = intOf(get(body, "sourcePage"));
        String issueType = strOf(get(body, "issueType"));
        String description = strOf(get(body, "description"));
        return R.ok(reviewService.createIssue(bookId, questionId, sourcePage, issueType, description));
    }

    /** 问题列表（bookId 必填，type/status/source 可选；source: human=人工 / agent=自查）。 */
    @SaCheckLogin
    @GetMapping("/issues")
    public R<List<Map<String, Object>>> issues(@RequestParam Long bookId,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String source) {
        return R.ok(reviewService.listIssues(bookId, type, status, source));
    }

    /** 页级置信度地图（速审跳页）：{totalPages, pages:[{page,items,minConf,tier,reviewed,issues}]}。 */
    @SaCheckLogin
    @GetMapping("/page-map")
    public R<Map<String, Object>> pageMap(@RequestParam Long bookId) {
        return R.ok(reviewService.pageMap(bookId));
    }

    /** 批量页级确认（高置信页一键通过）。body: {@code {bookId, pages:[..]}}。 */
    @SaCheckLogin
    @PutMapping("/confirm-pages")
    public R<Map<String, Object>> confirmPages(@RequestBody Map<String, Object> body) {
        Long bookId = idOf(get(body, "bookId"));
        List<Integer> pages = new java.util.ArrayList<>();
        Object raw = get(body, "pages");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                Integer p = intOf(o);
                if (p != null) pages.add(p);
            }
        }
        return R.ok(reviewService.confirmPages(bookId, pages));
    }

    /** 更新问题状态。body: {@code {status}}。 */
    @SaCheckLogin
    @PutMapping("/issue/{id}/status")
    public R<Map<String, Object>> updateIssueStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = strOf(get(body, "status"));
        return R.ok(reviewService.updateIssueStatus(id, status));
    }

    // ───────────────── body helpers（雪花号一律按字符串安全解析） ─────────────────

    private Object get(Map<String, Object> body, String key) {
        return body == null ? null : body.get(key);
    }

    private Long idOf(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOf(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return Integer.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String strOf(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
