package org.dromara.book.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 无头 Chrome HTML→PDF 渲染器（从 SpecialExportService 抽出的公共面）。
 *
 * <p>约定：主题目录在 classpath {@code export-themes/<theme>/}，含入口 HTML（内嵌
 * {@code __PAPER_DATA__} 注入点）与随附静态资源（katex 等）；每次渲染把整个主题目录
 * 拷到临时目录，注入 JSON 数据后以 {@code --print-to-pdf} 渲染，用毕即删。
 *
 * <p>调用方：专项双卷导出（sujunyu-v1）、计算题出题器（oralcalc-v1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChromePdfRenderer {

    private final ObjectMapper om;

    private static final String DATA_TOKEN = "__PAPER_DATA__";

    private static final String[] CHROME_CANDIDATES = {
        "C:/Program Files/Google/Chrome/Application/chrome.exe",
        "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
        System.getenv("LOCALAPPDATA") + "/Google/Chrome/Application/chrome.exe",
        "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
        "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
        "/usr/bin/google-chrome",
        "/usr/bin/chromium-browser",
        "/usr/bin/chromium"
    };

    /**
     * 单次渲染基线超时（单天/单卷绰绰有余）。
     * 🔴 整册导出**一次把 N 天渲成一个 PDF**，耗时随天数线性涨——10 天实测 60~70s，
     * 30 天更久。故实际超时 = 基线 + 每页预算 × 页数，见 {@link #timeoutFor(int)}。
     * 固定 60s 曾让 10 天的书成败各半、30 天必挂（2026-07-31 实测）。
     */
    private static final long CHROME_TIMEOUT_BASE_MS = 60_000L;
    /** 每页追加预算（整册合并渲染用）。 */
    private static final long CHROME_TIMEOUT_PER_PAGE_MS = 6_000L;
    /** 超时硬顶（防真卡死时线程被无限占）。 */
    private static final long CHROME_TIMEOUT_MAX_MS = 600_000L;

    private static long timeoutFor(int pages) {
        if (pages <= 1) return CHROME_TIMEOUT_BASE_MS;
        return Math.min(CHROME_TIMEOUT_BASE_MS + (long) pages * CHROME_TIMEOUT_PER_PAGE_MS,
            CHROME_TIMEOUT_MAX_MS);
    }

    /**
     * 🔴 全局 chrome 并发闸（2026-07-31 事故修复）。
     *
     * <p>整册导出是**逐天渲染再合并**，单次只渲一页本该 3~5s；但多个导出任务并发时
     * （两本书的 worker + 用户手点「导出本天」= 3 个 chrome 同时跑），容器内存与
     * /dev/shm 被瓜分，慢的超时、快的直接崩溃退出无产物——**症状是随机失败，不是必现**。
     *
     * <p>限并发后单任务稍慢但确定成功。等待上限 5 分钟（排队比失败强，但不能无限排）。
     */
    private static final java.util.concurrent.Semaphore CHROME_SLOTS =
        new java.util.concurrent.Semaphore(2, true);
    private static final long SLOT_WAIT_MS = 300_000L;

    /**
     * 渲染一份 PDF。
     *
     * @param themeDir  classpath 主题目录名（如 "sujunyu-v1"），入口 HTML 须同名（&lt;themeDir&gt;.html）
     * @param data      注入 __PAPER_DATA__ 的数据对象
     * @param tag       日志/临时文件标记（如 "question"/"answer"）
     */
    public byte[] render(String themeDir, Map<String, Object> data, String tag) {
        return render(themeDir, data, tag, 1);
    }

    /**
     * 渲染一份 PDF，并按页数放宽超时。
     *
     * @param pages 预计页数（整册合并渲染传天数；单卷传 1）。超时 = 基线 + 每页预算 × 页数。
     */
    public byte[] render(String themeDir, Map<String, Object> data, String tag, int pages) {
        Path work;
        try {
            work = Files.createTempDirectory("pdf-render-");
            copyTheme(themeDir, work);
        } catch (Exception e) {
            log.error("[pdf-render] 准备主题失败 theme={}", themeDir, e);
            throw new ServiceException("导出环境准备失败: " + e.getMessage(), 500);
        }
        try {
            return renderInWork(work, themeDir + ".html", data, tag, pages);
        } finally {
            deleteQuietly(work);
        }
    }

    /**
     * 只注入不渲染：返回主题入口 HTML 注入 data 后的字符串（FE iframe 预览用，与 {@link #render} 同源）。
     *
     * <p>🔴 所见即所得的构造保证（PRD-013 D3/G9）：预览与导出**同一主题、同一注入函数**，
     * 差别只在出口——本方法出 HTML 串，{@link #render} 把同一串喂无头 Chrome 出 PDF。
     *
     * <p>不落临时目录（不拷主题静态资源），故要求主题**单文件自包含**（punch-v1 已把水印内嵌
     * base64；带 katex/图片相对路径的主题不适用本出口）。
     *
     * @param themeDir classpath 主题目录名，入口 HTML 须同名（&lt;themeDir&gt;.html）
     * @param data     注入 __PAPER_DATA__ 的数据对象
     */
    public String renderHtml(String themeDir, Map<String, Object> data) {
        String tpl;
        try {
            PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver();
            Resource rc = r.getResource("classpath:export-themes/" + themeDir + "/" + themeDir + ".html");
            if (!rc.exists() || !rc.isReadable()) {
                throw new ServiceException("导出主题资源缺失（export-themes/" + themeDir + "）", 500);
            }
            try (InputStream in = rc.getInputStream()) {
                tpl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[pdf-render] 读取主题失败 theme={}", themeDir, e);
            throw new ServiceException("主题读取失败: " + e.getMessage(), 500);
        }
        try {
            String json = om.writeValueAsString(data);
            // 与 renderInWork 同一转义：防 </script> 提前闭合注入块（Jackson 默认不转义 /）
            json = json.replace("</", "<\\/");
            return tpl.replace(DATA_TOKEN, json);
        } catch (Exception e) {
            log.error("[pdf-render] 数据注入失败 theme={}", themeDir, e);
            throw new ServiceException("预览数据注入失败: " + e.getMessage(), 500);
        }
    }

    private byte[] renderInWork(Path work, String themeHtml, Map<String, Object> data,
                                String tag, int pages) {
        try {
            String json = om.writeValueAsString(data);
            // 防 </script> 提前闭合注入块（Jackson 默认不转义 /）：转义所有 </ 为 <\/。
            json = json.replace("</", "<\\/");
            String tpl = Files.readString(work.resolve(themeHtml), StandardCharsets.UTF_8);
            String html = tpl.replace(DATA_TOKEN, json);
            Path htmlOut = work.resolve("paper-" + tag + ".html");
            Files.writeString(htmlOut, html, StandardCharsets.UTF_8);
            Path pdfOut = work.resolve("paper-" + tag + ".pdf");

            String chrome = resolveChrome();
            List<String> cmd = new ArrayList<>();
            cmd.add(chrome);
            cmd.add("--headless=new");
            cmd.add("--disable-gpu");
            cmd.add("--no-sandbox");
            // 🔴 容器里 /dev/shm 默认只有 64MB，渲染大页面（整册几十页）共享内存耗尽 →
            //    渲染进程直接崩、chrome 退出但无产物（不是超时！2026-07-31 单天导出实测踩中，
            //    当时正并发跑整册导出，shm 被吃光）。改用 /tmp 落共享内存，容量随磁盘。
            cmd.add("--disable-dev-shm-usage");
            // 🔴 独立 profile：不带这个参数，并发的多个 chrome 实例共用默认 user-data-dir，
            //    撞 SingletonLock 后有的实例直接退出无产物。work 是每次渲染独有的临时目录。
            cmd.add("--user-data-dir=" + work.resolve("chrome-profile").toAbsolutePath());
            cmd.add("--no-pdf-header-footer");
            // 20s 虚拟时间预算：整卷可能十余张 OSS 外链图，预算过小图未加载完就打印（白框/缺图）。
            // 虚拟时钟在页面静止时快进，加大预算不增加正常卷的真实渲染耗时。
            cmd.add("--virtual-time-budget=20000");
            cmd.add("--run-all-compositor-stages-before-draw");
            cmd.add("--print-to-pdf=" + pdfOut.toAbsolutePath());
            cmd.add(htmlOut.toAbsolutePath().toUri().toString());

            // 🔴 stdout 重定向到文件而非进程内阻塞读，让 waitFor 守卫真正生效（历史坑）。
            Path logFile = work.resolve("chrome-" + tag + ".log");
            long timeoutMs = timeoutFor(pages);

            if (!CHROME_SLOTS.tryAcquire(SLOT_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new ServiceException("PDF 渲染排队超时（导出任务过多，稍后再试）", 503);
            }
            Process p = null;
            try {
                p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
                boolean done = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!done) {
                    p.destroyForcibly();
                    p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    throw new ServiceException("PDF 渲染超时（" + pages + " 页，超 "
                        + (timeoutMs / 1000) + "s）", 500);
                }
            } finally {
                CHROME_SLOTS.release();
            }

            if (!Files.exists(pdfOut) || Files.size(pdfOut) == 0) {
                String logtxt = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
                int exit = p.exitValue();
                log.error("[pdf-render] chrome 无产物 tag={} pages={} exit={} log={}", tag, pages, exit, logtxt);
                // 🔴 把 exit code 与 chrome 日志尾部带进异常：光说"无产物"没法定位
                //    （崩溃 / OOM / 参数错 症状相同）。
                String tail = logtxt.length() > 400 ? logtxt.substring(logtxt.length() - 400) : logtxt;
                throw new ServiceException("PDF 渲染失败（无产物，chrome exit=" + exit
                    + "）：" + tail.replaceAll("\\s+", " ").trim(), 500);
            }
            return Files.readAllBytes(pdfOut);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[pdf-render] 渲染异常 tag={}", tag, e);
            throw new ServiceException("PDF 渲染异常: " + e.getMessage(), 500);
        }
    }

    private String resolveChrome() {
        String env = System.getenv("CHROME_BIN");
        if (env != null && !env.isBlank() && Files.exists(Path.of(env))) return env;
        for (String c : CHROME_CANDIDATES) {
            if (c == null) continue;
            try {
                if (Files.exists(Path.of(c))) return c;
            } catch (Exception ignore) {
            }
        }
        throw new ServiceException("未找到 Chrome/Edge，设 CHROME_BIN 环境变量指定", 500);
    }

    private void copyTheme(String themeDir, Path work) throws Exception {
        String prefix = "export-themes/" + themeDir + "/";
        PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver();
        Resource[] res = r.getResources("classpath*:" + prefix + "**");
        int copied = 0;
        for (Resource rc : res) {
            if (!rc.isReadable()) continue;
            String uri = rc.getURI().toString();
            int idx = uri.indexOf(prefix);
            if (idx < 0) continue;
            String rel = uri.substring(idx + prefix.length());
            if (rel.isBlank() || rel.endsWith("/")) continue;
            Path dst = work.resolve(rel);
            Files.createDirectories(dst.getParent());
            try (InputStream in = rc.getInputStream()) {
                Files.copy(in, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        }
        if (copied == 0 || !Files.exists(work.resolve(themeDir + ".html"))) {
            throw new ServiceException("导出主题资源缺失（export-themes/" + themeDir + "）", 500);
        }
    }

    private void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(pp -> {
                try {
                    Files.deleteIfExists(pp);
                } catch (Exception ignore) {
                }
            });
        } catch (Exception ignore) {
        }
    }
}
