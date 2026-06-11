package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ExportPaperBo;
import org.dromara.book.domain.vo.ExportRecordVo;
import org.dromara.book.domain.vo.ExportRetryVo;
import org.dromara.book.domain.vo.ExportSubmitVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.service.IExportRecordService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 试卷导出 Controller（/teacher/export/...，PRD-A-014）。
 *
 * <p>路径前缀 {@code /teacher/}，命中 {@link MisiktEnvelopeAdvice} 自动转 envelope
 * （成功 {@code {code:1,message,response}}，FE 按 code!==1 判错）。
 *
 * <p>4 端点：
 * <ol>
 *   <li>POST /teacher/export/paper — 提交导出任务（单用户并发 1，G7）</li>
 *   <li>GET  /teacher/export/record/{id} — 查记录详情（越权 404，G4/G6）</li>
 *   <li>GET  /teacher/export/record/page — 分页（仅本人，G4）</li>
 *   <li>POST /teacher/export/record/{id}/retry — 重试（仅本人，G6）</li>
 * </ol>
 *
 * <p>越权 / 不存在统一返 {@code R.fail(404, ...)} → envelope {@code {code:404}}（FE 判错）。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/export")
@RequiredArgsConstructor
public class ExportController {

    private final IExportRecordService exportRecordService;

    /**
     * POST /teacher/export/paper — 提交试卷导出任务。
     *
     * <p>body {@code {paperId?, ids[], fileName, variant, watermark}}；
     * 返回 {@code {recordId, estimatedSeconds}}。单用户并发 1：已有在途任务则
     * ServiceException → envelope code:500（G7）。
     */
    @SaCheckLogin
    @PostMapping("/paper")
    public R<ExportSubmitVo> exportPaper(@RequestBody ExportPaperBo bo) {
        return R.ok(exportRecordService.submit(bo));
    }

    /**
     * GET /teacher/export/record/{id} — 查导出记录详情（轮询用）。
     *
     * <p>仅本人记录可见，越权 / 不存在返 {@code R.fail(404)}。
     */
    @SaCheckLogin
    @GetMapping("/record/{id}")
    public R<ExportRecordVo> getRecord(@PathVariable("id") Long id) {
        ExportRecordVo vo = exportRecordService.getRecord(id);
        if (vo == null) {
            return R.fail(404, "导出记录不存在或无权访问");
        }
        return R.ok(vo);
    }

    /**
     * GET /teacher/export/record/page — 分页查当前登录用户的导出记录。
     *
     * <p>user_id 由 LoginHelper 取（不信前端），仅返回本人记录（G4）。
     */
    @SaCheckLogin
    @GetMapping("/record/page")
    public R<MisiktPageVo<ExportRecordVo>> page(
        @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
        @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return R.ok(exportRecordService.pageMine(pageNum, pageSize));
    }

    /**
     * POST /teacher/export/record/{id}/retry — 重试导出。
     *
     * <p>仅本人记录可重试，越权 / 不存在返 {@code R.fail(404)}；以原 options 新建任务，
     * 返回新 {@code {recordId}}（G6）。
     */
    @SaCheckLogin
    @PostMapping("/record/{id}/retry")
    public R<ExportRetryVo> retry(@PathVariable("id") Long id) {
        ExportRetryVo vo = exportRecordService.retry(id);
        if (vo == null) {
            return R.fail(404, "导出记录不存在或无权访问");
        }
        return R.ok(vo);
    }
}
