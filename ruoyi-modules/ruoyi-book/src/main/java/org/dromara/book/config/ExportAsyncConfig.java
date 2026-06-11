package org.dromara.book.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 试卷导出异步线程池配置（PRD-A-014）。
 *
 * <p>大卷生成是 CPU + IO 活（拉几十张题图 + OpenPDF 拼装 + OSS 上传），必须与 web 线程隔离，
 * 不占 tomcat 请求线程。专用 bean {@code exportPdfExecutor}，{@code ExportPdfWorker.@Async}
 * 显式指定它。
 *
 * <p>容量：核心 2 / 最大 4 / 队列 50。单用户并发 1 由 DB 状态判（提交时查 status in 0,1），
 * 线程池容量只决定多用户并发上限。拒绝策略 CallerRunsPolicy（队列满时退化同步，不丢任务）。
 *
 * @author backend-dev
 */
@Configuration
@EnableAsync
public class ExportAsyncConfig {

    public static final String EXPORT_EXECUTOR = "exportPdfExecutor";

    @Bean(EXPORT_EXECUTOR)
    public Executor exportPdfExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("export-pdf-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等在途任务跑完再停（上限 60s）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
