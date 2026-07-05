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

    @SaCheckLogin
    @GetMapping("/plan/page")
    public R<List<Map<String, Object>>> page(@RequestParam(required = false) String targetType,
                                             @RequestParam(required = false) String keyword) {
        return R.ok(planService.page(targetType, keyword));
    }

    @SaCheckLogin
    @GetMapping("/plan/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(planService.detail(id));
    }

    @SaCheckLogin
    @PostMapping("/plan/{id}/copy")
    public R<Map<String, Object>> copy(@PathVariable Long id) {
        Long newId = planService.copy(id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(newId));
        return R.ok(r);
    }

    /** 批量 upsert 课次 → {lessonIds}。 */
    @SaCheckLogin
    @PostMapping("/plan/{id}/lessons")
    public R<Map<String, Object>> lessons(@PathVariable Long id, @RequestBody LessonBatchBo bo) {
        List<Long> ids = planService.upsertLessons(id, bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lessonIds", ids.stream().map(String::valueOf).toList());
        return R.ok(r);
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
}
