package org.dromara.book.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 批量录题作业异步线程池配置（PRD-A-002 路B）。
 *
 * <p>拆题是慢 IO 活（调 toolkit /split 单图 ~13s、整卷 ~40s），必须与 web 线程隔离，不占 tomcat 请求线程。
 * 专用 bean {@code ingestJobExecutor}，{@link org.dromara.book.service.IngestJobWorker} 的 {@code @Async} 显式指定它。
 *
 * <p>容量：核心 2 / 最大 4 / 队列 50 / CallerRunsPolicy（队列满时退化同步，不丢任务），与 {@link ExportAsyncConfig} 对称。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Configuration
@EnableAsync
public class IngestJobAsyncConfig {

    public static final String INGEST_JOB_EXECUTOR = "ingestJobExecutor";

    @Bean(INGEST_JOB_EXECUTOR)
    public Executor ingestJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ingest-job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
