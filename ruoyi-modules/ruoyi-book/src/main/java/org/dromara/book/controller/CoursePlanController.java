package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.CoursePlanBo;
import org.dromara.book.domain.bo.LessonBatchBo;
import org.dromara.book.domain.bo.LessonReorderBo;
import org.dromara.book.service.schedule.CoursePlanService;
import org.dromara.book.service.schedule.PaperSlotService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程计划 Controller（PRD-C-213 /teacher/schedule/plan）。命中 MisiktEnvelopeAdvice。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/schedule")
@RequiredArgsConstructor
public class CoursePlanController {

    private final CoursePlanService planService;
    private final PaperSlotService paperSlotService;

    @SaCheckLogin
    @PostMapping("/plan")
    public R<Map<String, Object>> create(@RequestBody CoursePlanBo bo) {
        Long id = planService.upsertPlan(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    @SaCheckLogin
    @PutMapping("/plan/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody CoursePlanBo bo) {
        bo.setId(id);
        planService.upsertPlan(bo);
        return R.ok();
    }

    /** 计划列表。S1：targetId 可选，按归属对象过滤。 */
    @SaCheckLogin
    @GetMapping("/plan/page")
    public R<Map<String, Object>> page(@RequestParam(required = false) String targetType,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long targetId) {
        return R.ok(planService.page(targetType, keyword, targetId));
    }

    @SaCheckLogin
    @GetMapping("/plan/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(planService.detail(id));
    }

    /** 深拷贝含课次 → 新计划 PlanVO（含 lessons）。 */
    @SaCheckLogin
    @PostMapping("/plan/{id}/copy")
    public R<Map<String, Object>> copy(@PathVariable Long id) {
        Long newId = planService.copy(id);
        return R.ok(planService.detail(newId));
    }

    /**
     * 批量 upsert 课次 → PlanLessonVO[]（对齐 FE：请求体=课次数组 PlanLessonBo[]，非 {lessons:[]}）。
     * 兼容旧格式 {lessons:[...]}（MCP 侧）：body 是数组走数组，是对象取其 lessons。
     */
    @SaCheckLogin
    @PostMapping("/plan/{id}/lessons")
    public R<List<Map<String, Object>>> lessons(@PathVariable Long id, @RequestBody Object body) {
        return R.ok(planService.upsertLessons(id, body));
    }

    @SaCheckLogin
    @DeleteMapping("/plan/lesson/{id}")
    public R<Void> deleteLesson(@PathVariable Long id) {
        planService.deleteLesson(id);
        return R.ok();
    }

    @SaCheckLogin
    @PutMapping("/plan/{id}/lessons/reorder")
    public R<Void> reorder(@PathVariable Long id, @RequestBody LessonReorderBo bo) {
        planService.reorderLessons(id, bo.getLessonIds());
        return R.ok();
    }

    /** 家长版两列长图导出 → {file,url}。 */
    @SaCheckLogin
    @PostMapping("/plan/{id}/parent-export")
    public R<Map<String, Object>> parentExport(@PathVariable Long id,
                                              @RequestParam(required = false) Long targetId) {
        return R.ok(planService.parentExport(id, targetId));
    }

    // ───────────────── PRD-B-101 专项卷位·绑定/解绑/标记已备好 ─────────────────

    /**
     * 绑定既有卷到卷位（D7 兜底：任意 mine 卷可挂）。body: {@code {"paperId": 123}}。
     * 返回 {@code {paperSlots, prepState}}（更新后卷位列表 + 推导备课态）。
     */
    @SaCheckLogin
    @PostMapping("/plan/lesson/{lessonId}/slot/{slotSeq}/bind")
    public R<Map<String, Object>> bindSlot(@PathVariable Long lessonId, @PathVariable Integer slotSeq,
                                           @RequestBody Map<String, Object> body) {
        Object pid = body == null ? null : body.get("paperId");
        if (pid == null || String.valueOf(pid).isBlank()) {
            throw new ServiceException("paperId 不能为空", 400);
        }
        Long paperId;
        try {
            paperId = Long.valueOf(String.valueOf(pid).trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("paperId 非法：" + pid, 400);
        }
        return R.ok(slotResult(paperSlotService.bindPaper(lessonId, slotSeq, paperId, true)));
    }

    /** 解绑卷位（paper_id 置 null，卷留库不删；🔴 自动清全课次 manual_ready）。返回 {paperSlots, prepState}。 */
    @SaCheckLogin
    @PostMapping("/plan/lesson/{lessonId}/slot/{slotSeq}/unbind")
    public R<Map<String, Object>> unbindSlot(@PathVariable Long lessonId, @PathVariable Integer slotSeq) {
        return R.ok(slotResult(paperSlotService.unbindSlot(lessonId, slotSeq)));
    }

    /** 标记已备好 / 取消（manual_ready 覆盖，课次级）。body: {@code {"ready": true}}。返回 {paperSlots, prepState}。 */
    @SaCheckLogin
    @PostMapping("/plan/lesson/{lessonId}/manual-ready")
    public R<Map<String, Object>> manualReady(@PathVariable Long lessonId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        boolean ready = body == null || body.get("ready") == null
            || Boolean.parseBoolean(String.valueOf(body.get("ready")));
        return R.ok(slotResult(paperSlotService.markReady(lessonId, ready)));
    }

    private Map<String, Object> slotResult(List<Map<String, Object>> slots) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("paperSlots", slots);
        r.put("prepState", paperSlotService.deriveStatusFromList(slots));
        return r;
    }
}
