package org.dromara.book.service.punch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.config.ExportAsyncConfig;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 打卡书整册导出异步 Worker（2026-07-30 用户点单：异步 + 持久化 + 覆盖）。
 *
 * <p>范式对齐 {@code ExportPdfWorker}（PRD-A-014）：跑在专用池 {@code exportPdfExecutor}，
 * 内层 single-thread + {@code CompletableFuture.get(10min)} 超时守护。区别是零 DDL——
 * 状态/结果不建表，覆盖写 {@code biz_shelf_book.style_meta_json.punchExport}
 * （running → done{questionUrl,answerUrl}/failed{error}），重导天然盖掉上一次。
 *
 * <p>🔴 异步线程无 SaToken 上下文：只走 {@link PunchService} 的无门禁内部口
 * （renderBookMerged / writePunchExport），权限已在提交线程校验完；
 * mapper 层租户拦截由 BizBaseMapper 基类兜住，不需要登录态。
 *
 * @author backend-dev (PRD-013 bug批2)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PunchExportWorker {

    /** 单任务超时上限 10 分钟（10 天双卷 = 20 次 Chrome 渲染，实测 2-5min，留一倍余量）。 */
    private static final long TASK_TIMEOUT_MS = 10 * 60 * 1000L;

    private final PunchService punchService;

    /**
     * 异步执行整册导出：逐卷渲染合并 → OSS → 覆盖写 punchExport=done；任意失败/超时写 failed。
     *
     * @param bookId 打卡书 id（提交线程已 owner 校验）
     * @param papers 要出的卷（question/answer，已归一非空）
     * @param days   要合并的天序（提交线程已取好，避免异步线程再走带门禁的 listDays）
     */
    @Async(ExportAsyncConfig.EXPORT_EXECUTOR)
    public void run(Long bookId, List<String> papers, List<Integer> days) {
        run(bookId, papers, days, PunchService.FORMAT_PDF);
    }

    /** @param format {@code pdf} 整册合并 PDF | {@code png} 逐页图片 zip（发小红书用） */
    @Async(ExportAsyncConfig.EXPORT_EXECUTOR)
    public void run(Long bookId, List<String> papers, List<Integer> days, String format) {
        long startMs = System.currentTimeMillis();
        ExecutorService inner = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "punch-export-inner-" + bookId);
            t.setDaemon(true);
            return t;
        });
        try {
            CompletableFuture<Void> future =
                CompletableFuture.runAsync(() -> doRun(bookId, papers, days, startMs, format), inner);
            future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[punch-export] 整册导出超时 10min，置失败 bookId={}", bookId);
            markFailed(bookId, "导出超时（超过 10 分钟），请重试");
        } catch (Exception e) {
            log.error("[punch-export] 整册导出异常 bookId={}", bookId, e);
            markFailed(bookId, "导出异常: " + safeMsg(e));
        } finally {
            inner.shutdownNow();
            log.info("[punch-export] 任务结束 bookId={} 耗时={}ms", bookId, System.currentTimeMillis() - startMs);
        }
    }

    private void doRun(Long bookId, List<String> papers, List<Integer> days, long startMs) {
        doRun(bookId, papers, days, startMs, PunchService.FORMAT_PDF);
    }

    /**
     * @param format {@code pdf} = 整册合并 PDF；{@code png} = 逐页图片打 zip
     *               （小红书发的是图，PDF 发不了；一天一页 → zip 内一天一张）
     */
    private void doRun(Long bookId, List<String> papers, List<Integer> days, long startMs, String format) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", "done");
        state.put("papers", papers);
        state.put("days", days.size());
        state.put("format", format);
        OssClient oss = OssFactory.instance();
        for (String paper : papers) {
            byte[] merged = punchService.renderBookMerged(bookId, paper, days);
            String url;
            if (PunchService.FORMAT_PNG.equals(format)) {
                byte[] zip = zipPages(punchService.pdfAllPagesToPng(merged), paper);
                url = oss.uploadSuffix(zip, ".zip", "application/zip").getUrl();
            } else {
                url = oss.uploadSuffix(merged, ".pdf", "application/pdf").getUrl();
            }
            state.put("question".equals(paper) ? "questionUrl" : "answerUrl", url);
        }
        state.put("exportedAt", LocalDateTime.now().toString());
        state.put("durationMs", System.currentTimeMillis() - startMs);
        punchService.writePunchExport(bookId, state);
        log.info("[punch-export] 完成 bookId={} papers={} days={} format={} 耗时={}ms",
            bookId, papers, days.size(), format, state.get("durationMs"));
    }

    /** 逐页 PNG → zip（条目名「第01天-题目.png」，解压即按天有序）。 */
    private byte[] zipPages(List<byte[]> pages, String paper) {
        String label = "question".equals(paper) ? "题目" : "解析";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            for (int i = 0; i < pages.size(); i++) {
                zos.putNextEntry(new java.util.zip.ZipEntry(
                    String.format("第%02d天-%s.png", i + 1, label)));
                zos.write(pages.get(i));
                zos.closeEntry();
            }
        } catch (Exception e) {
            log.error("[punch-export] 图片打包失败 paper={}", paper, e);
            throw new org.dromara.common.core.exception.ServiceException("图片打包失败: " + e.getMessage(), 500);
        }
        return bos.toByteArray();
    }

    private void markFailed(Long bookId, String msg) {
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("status", "failed");
            state.put("error", msg != null && msg.length() > 300 ? msg.substring(0, 300) : msg);
            state.put("failedAt", LocalDateTime.now().toString());
            punchService.writePunchExport(bookId, state);
        } catch (Exception ex) {
            log.error("[punch-export] 写失败态也失败 bookId={}", bookId, ex);
        }
    }

    private String safeMsg(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String m = cause.getMessage();
        return m == null ? cause.getClass().getSimpleName() : m;
    }
}
