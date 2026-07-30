package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.FeedbackPlanExportBo;
import org.dromara.book.domain.bo.FeedbackSheetBo;
import org.dromara.book.service.schedule.FeedbackSheetService;
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
import java.util.Map;

/**
 * 课后反馈单 Controller（PRD-004 /teacher/feedback）。命中 MisiktEnvelopeAdvice（envelope）。
 *
 * <p>🔴 收数学正文（学习内容/不足点可含 &lt; &gt; 不等式）→ application.yml xss.excludeUrls
 * 已加 /teacher/feedback/**，否则 XssFilter 静默剥 &lt;...&gt; 腐蚀内容。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/feedback")
@RequiredArgsConstructor
public class FeedbackSheetController {

    private final FeedbackSheetService feedbackService;

    /** 建反馈单 → {id}。 */
    @SaCheckLogin
    @PostMapping("/sheet")
    public R<Map<String, Object>> create(@RequestBody FeedbackSheetBo bo) {
        bo.setId(null);
        Long id = feedbackService.upsert(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    /** 改反馈单。 */
    @SaCheckLogin
    @PutMapping("/sheet/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody FeedbackSheetBo bo) {
        bo.setId(id);
        feedbackService.upsert(bo);
        return R.ok();
    }

    /** 列表（owner，可选 targetId / keyword / batchKey / planId）→ {rows,total}。 */
    @SaCheckLogin
    @GetMapping("/sheet/page")
    public R<Map<String, Object>> page(@RequestParam(required = false) Long targetId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String batchKey,
                                       @RequestParam(required = false) Long planId) {
        return R.ok(feedbackService.page(targetId, keyword, batchKey, planId));
    }

    /** 详情（含 rows）。 */
    @SaCheckLogin
    @GetMapping("/sheet/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(feedbackService.detail(id));
    }

    /** 删除。 */
    @SaCheckLogin
    @DeleteMapping("/sheet/{id}")
    public R<Void> delete(@PathVariable Long id) {
        feedbackService.delete(id);
        return R.ok();
    }

    /** 导出家长版 PNG → {file,url}（下载复用 /teacher/schedule/artifact）。 */
    @SaCheckLogin
    @PostMapping("/sheet/{id}/export-png")
    public R<Map<String, Object>> exportPng(@PathVariable Long id) {
        return R.ok(feedbackService.exportPng(id));
    }

    /**
     * 批次全量导出家长版长图（PRD-010）：该学生一个批次 1~N 节全部反馈单按课次拼一张 PNG。
     * batchKey 缺省 = 该生最新批次（发送单位=批次全量，用户实发形态）。
     */
    @SaCheckLogin
    @PostMapping("/batch/export-png")
    public R<Map<String, Object>> exportBatchPng(@RequestParam Long targetId,
                                                 @RequestParam(required = false) String batchKey) {
        return R.ok(feedbackService.exportBatchPng(targetId, batchKey));
    }

    /**
     * 按课程计划导出反馈图（PRD-015 D7/D13）：{planId, mode:'single'|'long'}，mode 缺省 'single'。
     * single = 该计划最新一单（lesson_seq 最大）单张；long = 全量按序号升序拼长图。
     * 🔴 版式沿用现有导出模板，只改黄条标题=「序号 · 上课日期（· 备注）」，无"第几次"字样。
     */
    @SaCheckLogin
    @PostMapping("/export-plan-png")
    public R<Map<String, Object>> exportPlanPng(@RequestBody FeedbackPlanExportBo bo) {
        return R.ok(feedbackService.exportPlanPng(bo == null ? null : bo.getPlanId(),
            bo == null ? null : bo.getMode()));
    }
}
