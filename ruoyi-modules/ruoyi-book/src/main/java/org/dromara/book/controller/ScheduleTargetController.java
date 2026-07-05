package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ClassStudentsBo;
import org.dromara.book.domain.bo.RebindPlanBo;
import org.dromara.book.domain.bo.ScheduleTargetBo;
import org.dromara.book.service.schedule.ScheduleSessionService;
import org.dromara.book.service.schedule.ScheduleTargetService;
import org.dromara.common.core.domain.R;
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
 * 对象档案 Controller（PRD-C-213 /teacher/schedule/target + /class）。命中 MisiktEnvelopeAdvice。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/schedule")
@RequiredArgsConstructor
public class ScheduleTargetController {

    private final ScheduleTargetService targetService;
    private final ScheduleSessionService sessionService;

    /** 建档（color 空则色板轮转） → {id}。 */
    @SaCheckLogin
    @PostMapping("/target")
    public R<Map<String, Object>> create(@RequestBody ScheduleTargetBo bo) {
        Long id = targetService.create(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    /** 改基本维。 */
    @SaCheckLogin
    @PutMapping("/target/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody ScheduleTargetBo bo) {
        targetService.update(id, bo);
        return R.ok();
    }

    /** 卡片墙（含实时聚合）。 */
    @SaCheckLogin
    @GetMapping("/target/page")
    public R<Map<String, Object>> page(@RequestParam(required = false) String targetType,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        return R.ok(targetService.page(targetType, keyword, includeArchived));
    }

    /** 详情含 profile。 */
    @SaCheckLogin
    @GetMapping("/target/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "0") String targetType) {
        return R.ok(targetService.detail(id, targetType));
    }

    /** 归档（归档≠删，不进排课选择器）。 */
    @SaCheckLogin
    @PostMapping("/target/{id}/archive")
    public R<Void> archive(@PathVariable Long id) {
        targetService.archiveAuto(id, "1");
        return R.ok();
    }

    /** 取消归档。 */
    @SaCheckLogin
    @PostMapping("/target/{id}/unarchive")
    public R<Void> unarchive(@PathVariable Long id) {
        targetService.archiveAuto(id, "0");
        return R.ok();
    }

    /**
     * 整体覆写 profile_json（老师改肖像，含转正/删除 pending 信号）。
     * FE 传 {profileJson:{...}} → 解包取 profileJson 存；兼容直接传 profile 对象（MCP）。
     */
    @SaCheckLogin
    @PutMapping("/target/{id}/profile")
    public R<Void> profile(@PathVariable Long id,
                          @RequestParam(required = false, defaultValue = "0") String targetType,
                          @RequestBody Object body) {
        Object profile = body;
        if (body instanceof Map<?, ?> mp && mp.containsKey("profileJson")) {
            profile = mp.get("profileJson");
        }
        targetService.overwriteProfile(id, targetType, profile);
        return R.ok();
    }

    /** 设班课成员。 */
    @SaCheckLogin
    @PostMapping("/class/{id}/students")
    public R<Void> classStudents(@PathVariable Long id, @RequestBody ClassStudentsBo bo) {
        targetService.setClassStudents(id, bo);
        return R.ok();
    }

    /**
     * 换绑计划（R1a·简版真做）：把该对象未上（status='0' 且日期>=今天）的场次整体切到新计划，
     * 课次依 lesson_seq 顺配，多余场次置 NULL，plan_id 同步改 → {rebound, unbound}。
     * S1 校验：新计划必须归属该对象。
     */
    @SaCheckLogin
    @PostMapping("/target/{targetType}/{targetId}/rebind-plan")
    public R<Map<String, Object>> rebindPlan(@PathVariable String targetType,
                                             @PathVariable Long targetId,
                                             @RequestBody RebindPlanBo bo) {
        return R.ok(sessionService.rebindPlan(targetType, targetId, bo.getNewPlanId()));
    }
}
