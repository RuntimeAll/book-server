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

    private static final long CHROME_TIMEOUT_MS = 60_000L;

    /**
     * 渲染一份 PDF。
     *
     * @param themeDir  classpath 主题目录名（如 "sujunyu-v1"），入口 HTML 须同名（&lt;themeDir&gt;.html）
     * @param data      注入 __PAPER_DATA__ 的数据对象
     * @param tag       日志/临时文件标记（如 "question"/"answer"）
     */
    public byte[] render(String themeDir, Map<String, Object> data, String tag) {
        Path work;
        try {
            work = Files.createTempDirectory("pdf-render-");
            copyTheme(themeDir, work);
        } catch (Exception e) {
            log.error("[pdf-render] 准备主题失败 theme={}", themeDir, e);
            throw new ServiceException("导出环境准备失败: " + e.getMessage(), 500);
        }
        try {
            return renderInWork(work, themeDir + ".html", data, tag);
        } finally {
            deleteQuietly(work);
        }
    }

    private byte[] renderInWork(Path work, String themeHtml, Map<String, Object> data, String tag) {
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
            cmd.add("--no-pdf-header-footer");
            // 20s 虚拟时间预算：整卷可能十余张 OSS 外链图，预算过小图未加载完就打印（白框/缺图）。
            // 虚拟时钟在页面静止时快进，加大预算不增加正常卷的真实渲染耗时。
            cmd.add("--virtual-time-budget=20000");
            cmd.add("--run-all-compositor-stages-before-draw");
            cmd.add("--print-to-pdf=" + pdfOut.toAbsolutePath());
            cmd.add(htmlOut.toAbsolutePath().toUri().toString());

            // 🔴 stdout 重定向到文件而非进程内阻塞读，让 waitFor(60s) 守卫真正生效（历史坑）。
            Path logFile = work.resolve("chrome-" + tag + ".log");
            Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
            boolean done = p.waitFor(CHROME_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                throw new ServiceException("PDF 渲染超时（超 " + (CHROME_TIMEOUT_MS / 1000) + "s）", 500);
            }
            if (!Files.exists(pdfOut) || Files.size(pdfOut) == 0) {
                String logtxt = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
                log.error("[pdf-render] chrome 无产物 exit={} log={}", p.exitValue(), logtxt);
                throw new ServiceException("PDF 渲染失败（无产物）", 500);
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
