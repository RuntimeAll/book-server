package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ConflictCheckBo;
import org.dromara.book.domain.bo.SessionBatchBo;
import org.dromara.book.domain.bo.SessionReviewBo;
import org.dromara.book.domain.bo.SessionUpdateBo;
import org.dromara.book.service.schedule.ScheduleSessionService;
import org.dromara.book.service.schedule.SessionReviewService;
import org.dromara.book.util.ScheduleRenderUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 排课 Controller（PRD-C-213 /teacher/schedule/session + calendar + stat + prep + artifact + review）。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/schedule")
@RequiredArgsConstructor
public class ScheduleSessionController {

    private final ScheduleSessionService sessionService;
    private final SessionReviewService reviewService;
    private final ScheduleRenderUtil renderUtil;

    // ─────────────────────── 排课 ───────────────────────

    @SaCheckLogin
    @PostMapping("/session/batch")
    public R<Map<String, Object>> batch(@RequestBody SessionBatchBo bo) {
        return R.ok(sessionService.batch(bo));
    }

    @SaCheckLogin
    @PostMapping("/session/conflict-check")
    public R<Map<String, Object>> conflictCheck(@RequestBody ConflictCheckBo bo) {
        return R.ok(sessionService.conflictCheck(bo.getTargetType(), bo.getTargetId(), bo.getItems()));
    }

    @SaCheckLogin
    @GetMapping("/session/calendar")
    public R<List<Map<String, Object>>> calendar(@RequestParam(required = false) String start,
                                                 @RequestParam(required = false) String end,
                                                 @RequestParam(required = false) Long targetId) {
        return R.ok(sessionService.calendar(start, end, targetId));
    }

    @SaCheckLogin
    @GetMapping("/session/page")
    public R<List<Map<String, Object>>> page(@RequestParam(required = false) Long targetId,
                                            @RequestParam(required = false) String status) {
        return R.ok(sessionService.page(targetId, status));
    }

    @SaCheckLogin
    @PutMapping("/session/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SessionUpdateBo bo) {
        sessionService.update(id, bo);
        return R.ok();
    }

    @SaCheckLogin
    @PostMapping("/session/{id}/leave")
    public R<Map<String, Object>> leave(@PathVariable Long id) {
        return R.ok(sessionService.leaveOrCancel(id, "2"));
    }

    @SaCheckLogin
    @PostMapping("/session/{id}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable Long id) {
        return R.ok(sessionService.leaveOrCancel(id, "3"));
    }

    @SaCheckLogin
    @PostMapping("/session/{id}/mark-done")
    public R<Void> markDone(@PathVariable Long id) {
        sessionService.markDone(id);
        return R.ok();
    }

    @SaCheckLogin
    @PostMapping("/session/{id}/lock")
    public R<Void> lock(@PathVariable Long id) {
        sessionService.lock(id, true);
        return R.ok();
    }

    @SaCheckLogin
    @PostMapping("/session/{id}/unlock")
    public R<Void> unlock(@PathVariable Long id) {
        sessionService.lock(id, false);
        return R.ok();
    }

    // ─────────────────────── 回收 ───────────────────────

    @SaCheckLogin
    @PostMapping("/session/{id}/review")
    public R<Map<String, Object>> review(@PathVariable Long id, @RequestBody SessionReviewBo bo) {
        return R.ok(reviewService.submit(id, bo));
    }

    @SaCheckLogin
    @GetMapping("/session/{id}/review")
    public R<Map<String, Object>> getReview(@PathVariable Long id) {
        return R.ok(reviewService.get(id));
    }

    // ─────────────────────── 统计 / 待备 ───────────────────────

    @SaCheckLogin
    @GetMapping("/stat/overview")
    public R<Map<String, Object>> statOverview() {
        return R.ok(sessionService.statOverview());
    }

    @SaCheckLogin
    @GetMapping("/prep/todo")
    public R<List<Map<String, Object>>> prepTodo(@RequestParam(required = false, defaultValue = "14") int days) {
        return R.ok(sessionService.prepTodo(days));
    }

    // ─────────────────────── 产物下载（二进制不走 envelope） ───────────────────────

    /**
     * 限 artifact-dir 内防路径穿越。直写 HttpServletResponse（setContentType 覆盖式，避免全局
     * 过滤器已置的 application/json 与本响应叠成双 Content-Type 头）。二进制不走 envelope。
     */
    @SaCheckLogin
    @GetMapping("/artifact")
    public void artifact(@RequestParam String path, HttpServletResponse response) {
        try {
            Path root = renderUtil.resolveArtifactRoot();
            Path target = root.resolve(path).normalize();
            if (!target.startsWith(root)) {
                throw new ServiceException("非法路径");
            }
            if (!Files.exists(target)) {
                throw new ServiceException("产物不存在");
            }
            byte[] bytes = Files.readAllBytes(target);
            String fn = target.getFileName().toString();
            String mt = fn.endsWith(".pdf") ? "application/pdf"
                : fn.endsWith(".png") ? "image/png" : "application/octet-stream";
            response.reset();
            response.setContentType(mt);
            response.setContentLength(bytes.length);
            response.setHeader("Content-Disposition", "inline; filename=\"" + fn + "\"");
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("下载失败：" + e.getMessage());
        }
    }
}
