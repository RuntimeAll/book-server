package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.CoursePlanBo;
import org.dromara.book.domain.bo.LessonBatchBo;
import org.dromara.book.domain.bo.LessonReorderBo;
import org.dromara.book.service.schedule.CoursePlanService;
import org.dromara.common.core.domain.R;
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

    // ───────────────── PRD-003 D7：卷位绑定/解绑/标记已备好端点已退役 ─────────────────
    // B-101 卷位链（slot bind/unbind + manual-ready）随 D7 下线，课次备课统一走专项材料位
    // （SpecialController /teacher/special/lesson/{id}/bind|unbind|materials）。备课态由 special_ids 推导。
}
