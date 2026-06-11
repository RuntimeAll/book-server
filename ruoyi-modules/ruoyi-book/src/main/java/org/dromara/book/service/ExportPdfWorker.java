package org.dromara.book.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.config.ExportAsyncConfig;
import org.dromara.book.domain.bo.ExportOptions;
import org.dromara.book.domain.entity.BizExportRecord;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.mapper.BizExportRecordMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 试卷导出异步 Worker（PRD-A-014）。
 *
 * <p>在专用线程池 {@code exportPdfExecutor} 跑（与 web 线程隔离）：load record → 解析 options →
 * 按 ids 题序拉题 → {@link PdfComposer} 拼图 + 水印 → OSS 上传 → 更新 record(status=2/file_url/
 * duration_ms)。任意失败 status=3 + error_msg。**任务超时 5min → status=3**（用内层
 * single-thread 跑 + CompletableFuture.get(5min) 守护，超时打断置失败）。
 *
 * <p>单用户并发 1 的判定在 {@link IExportRecordService#submit} 入口（DB status in 0,1），
 * Worker 只管单任务执行。
 *
 * @author backend-dev
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportPdfWorker {

    /** 单任务超时上限 5 分钟 */
    private static final long TASK_TIMEOUT_MS = 5 * 60 * 1000L;

    private final BizExportRecordMapper exportRecordMapper;
    private final BizQuestionMapper questionMapper;
    private final PdfComposer pdfComposer;
    private final ObjectMapper objectMapper;
    private final WatermarkTextResolver watermarkTextResolver;

    /**
     * 异步执行导出任务。
     *
     * @param recordId 任务记录 id
     */
    @Async(ExportAsyncConfig.EXPORT_EXECUTOR)
    public void run(Long recordId) {
        long startMs = System.currentTimeMillis();
        // 用单线程内层池 + 超时守护：超 5min 打断置失败
        ExecutorService inner = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "export-pdf-inner-" + recordId);
            t.setDaemon(true);
            return t;
        });
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> doRun(recordId), inner);
            future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[export-worker] 任务超时 5min，置失败 recordId={}", recordId);
            markFailed(recordId, "导出超时（超过 5 分钟）");
        } catch (Exception e) {
            log.error("[export-worker] 任务异常 recordId={} err={}", recordId, e.getMessage(), e);
            markFailed(recordId, "导出异常: " + safeMsg(e));
        } finally {
            inner.shutdownNow();
            log.info("[export-worker] 任务结束 recordId={} 耗时={}ms", recordId, System.currentTimeMillis() - startMs);
        }
    }

    /**
     * 实际生成逻辑（在内层守护线程跑）。
     */
    private void doRun(Long recordId) {
        BizExportRecord record = exportRecordMapper.selectById(recordId);
        if (record == null) {
            log.warn("[export-worker] record 不存在 recordId={}", recordId);
            return;
        }
        long startMs = System.currentTimeMillis();

        // 1. 置生成中
        updateProgress(recordId, BizExportRecord.STATUS_RUNNING, 10);

        // 2. 解析 options
        ExportOptions options;
        try {
            options = objectMapper.readValue(record.getOptions(), ExportOptions.class);
        } catch (Exception e) {
            markFailed(recordId, "导出配置解析失败");
            return;
        }
        List<String> idStrs = options.getIds();
        if (idStrs == null || idStrs.isEmpty()) {
            markFailed(recordId, "导出题目为空");
            return;
        }

        // 3. 按 ids 题序拉题（DB in 查询不保序 → 拉回后按 idStrs 顺序重排）
        List<Long> ids = new ArrayList<>(idStrs.size());
        for (String s : idStrs) {
            try {
                ids.add(Long.valueOf(s.trim()));
            } catch (NumberFormatException ignore) {
                // 跳过非法 id
            }
        }
        List<BizQuestion> fetched = ids.isEmpty() ? new ArrayList<>() : questionMapper.selectByIds(ids);
        Map<Long, BizQuestion> byId = new HashMap<>();
        for (BizQuestion q : fetched) {
            byId.put(q.getId(), q);
        }
        List<BizQuestion> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            BizQuestion q = byId.get(id);
            if (q != null) {
                ordered.add(q);
            }
        }
        if (ordered.isEmpty()) {
            markFailed(recordId, "题目不存在或已删除");
            return;
        }
        updateProgress(recordId, BizExportRecord.STATUS_RUNNING, 40);

        // 4. 拼 PDF — showAnswer/showExplain 双开关（2026-06-11 契约改版；存量 options 只有
        //    旧 variant 字段 → 映射：teacher=答案+解析 / student_answers_appended=答案 / 其余=仅题干）
        boolean showAnswer = options.getShowAnswer() != null
            ? options.getShowAnswer()
            : "teacher".equals(options.getVariant()) || "student_answers_appended".equals(options.getVariant());
        boolean showExplain = options.getShowExplain() != null
            ? options.getShowExplain()
            : "teacher".equals(options.getVariant());
        boolean watermark = Boolean.TRUE.equals(options.getWatermark());
        String wmText = watermark ? watermarkTextResolver.resolve(record.getUserId()) : null;
        byte[] pdf = pdfComposer.compose(record.getFileName(), ordered, showAnswer, showExplain, watermark, wmText);
        updateProgress(recordId, BizExportRecord.STATUS_RUNNING, 75);

        // 5. OSS 上传
        OssClient storage = OssFactory.instance();
        UploadResult uploaded = storage.uploadSuffix(pdf, ".pdf", "application/pdf");

        // 6. 完成落库
        long durationMs = System.currentTimeMillis() - startMs;
        BizExportRecord done = new BizExportRecord();
        done.setId(recordId);
        done.setStatus(BizExportRecord.STATUS_DONE);
        done.setProgress(100);
        done.setFileUrl(uploaded.getUrl());
        done.setFileSize((long) pdf.length);
        done.setDurationMs((int) durationMs);
        // 2026-06-11 用户拍板：不设过期（expire_at 不再写），文件留存到用户自己删
        done.setUpdateTime(new Date());
        exportRecordMapper.updateById(done);
        log.info("[export-worker] 完成 recordId={} 题数={} size={}B 耗时={}ms url={}",
            recordId, ordered.size(), pdf.length, durationMs, uploaded.getUrl());
    }

    private void updateProgress(Long recordId, String status, int progress) {
        BizExportRecord upd = new BizExportRecord();
        upd.setId(recordId);
        upd.setStatus(status);
        upd.setProgress(progress);
        upd.setUpdateTime(new Date());
        exportRecordMapper.updateById(upd);
    }

    private void markFailed(Long recordId, String msg) {
        BizExportRecord upd = new BizExportRecord();
        upd.setId(recordId);
        upd.setStatus(BizExportRecord.STATUS_FAILED);
        upd.setErrorMsg(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
        upd.setUpdateTime(new Date());
        exportRecordMapper.updateById(upd);
    }

    private String safeMsg(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String m = cause.getMessage();
        return m == null ? cause.getClass().getSimpleName() : m;
    }
}
