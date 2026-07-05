package org.dromara.book.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 教学安排渲染工具（PRD-C-213）：HTML 模板拼装 + Edge headless 出 PDF/PNG。
 *
 * <p>PDF（备课包）对照 09a/b/c；PNG（家长版）对照 06。浏览器路径 yml 可配
 * {@code schedule.pdf.browser-path}，默认探测两处 Edge 安装位置；产物落
 * {@code schedule.artifact-dir}（默认 ./prep-artifacts）。
 *
 * <p>🔴 PDF 只有题目，任何答案/解析字段不得进 HTML；家长版内部字段一律不出现。
 *
 * @author backend-dev
 */
@Slf4j
@Component
public class ScheduleRenderUtil {

    private static final String[] EDGE_PROBE = {
        "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
        "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
    };

    @Value("${schedule.pdf.browser-path:}")
    private String browserPath;

    @Value("${schedule.artifact-dir:./prep-artifacts}")
    private String artifactDir;

    @Getter
    public static class PdfResult {
        private final String file;
        private final int pages;
        public PdfResult(String file, int pages) { this.file = file; this.pages = pages; }
    }

    private Path artifactRoot() {
        try {
            Path root = Paths.get(artifactDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
            return root;
        } catch (Exception e) {
            throw new ServiceException("产物目录创建失败：" + e.getMessage());
        }
    }

    /** 产物根目录绝对路径（供下载端点防穿越校验用）。 */
    public Path resolveArtifactRoot() {
        return artifactRoot();
    }

    private String resolveBrowser() {
        if (browserPath != null && !browserPath.isBlank() && new File(browserPath).exists()) {
            return browserPath;
        }
        for (String p : EDGE_PROBE) {
            if (new File(p).exists()) return p;
        }
        throw new ServiceException("未找到 Edge 浏览器，请在 yml 配置 schedule.pdf.browser-path");
    }

    /** HTML → PDF（--print-to-pdf）。返回相对文件名 + 页数。 */
    public PdfResult printToPdf(String html, String name) {
        Path root = artifactRoot();
        String base = safe(name);
        Path htmlFile = root.resolve(base + ".html");
        Path pdfFile = root.resolve(base + ".pdf");
        try {
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
            runEdge(new String[]{
                resolveBrowser(), "--headless=new", "--disable-gpu", "--no-sandbox",
                "--virtual-time-budget=6000", "--run-all-compositor-stages-before-draw",
                "--print-to-pdf=" + pdfFile.toAbsolutePath(),
                htmlFile.toUri().toString()
            });
            if (!Files.exists(pdfFile) || Files.size(pdfFile) == 0) {
                throw new ServiceException("PDF 生成失败（产物为空）：" + base);
            }
            int pages = countPdfPages(pdfFile);
            return new PdfResult(pdfFile.getFileName().toString(), pages);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("PDF 渲染异常：" + e.getMessage());
        }
    }

    /** HTML → PNG（--screenshot）。返回相对文件名。 */
    public String screenshot(String html, String name, int width, int height) {
        Path root = artifactRoot();
        String base = safe(name);
        Path htmlFile = root.resolve(base + ".html");
        Path pngFile = root.resolve(base + ".png");
        try {
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
            runEdge(new String[]{
                resolveBrowser(), "--headless=new", "--disable-gpu", "--no-sandbox",
                "--hide-scrollbars", "--force-device-scale-factor=1",
                "--virtual-time-budget=6000",
                "--screenshot=" + pngFile.toAbsolutePath(),
                "--window-size=" + width + "," + height,
                htmlFile.toUri().toString()
            });
            if (!Files.exists(pngFile) || Files.size(pngFile) == 0) {
                throw new ServiceException("PNG 生成失败（产物为空）：" + base);
            }
            return pngFile.getFileName().toString();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("PNG 渲染异常：" + e.getMessage());
        }
    }

    private void runEdge(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        // 读掉输出防阻塞
        try (var in = proc.getInputStream()) {
            in.readAllBytes();
        }
        boolean done = proc.waitFor(60, TimeUnit.SECONDS);
        if (!done) {
            proc.destroyForcibly();
            throw new ServiceException("浏览器渲染超时");
        }
    }

    private int countPdfPages(Path pdf) {
        try {
            String raw = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
            int a = count(raw, "/Type\\s*/Page[^s]");
            int b = count(raw, "/MediaBox");
            return Math.max(1, Math.max(a, b));
        } catch (Exception e) {
            return 1;
        }
    }

    private int count(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private String safe(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // ─────────────────────────── HTML 模板 ───────────────────────────

    /**
     * 备课包一段一卷 HTML（对照 09a/b/c）。
     * 专项段（style 含 ★ 或有 rules）= 核心口诀 kj 框 + 三层星级；否则朴素题列。
     * 🔴 仅题目无答案解析。
     *
     * @param segName   段名（思维题/奥数专项/课内同步…）
     * @param segStyle  段风格文案
     * @param segTopic  段主题（卷头标题）
     * @param segNote   核心口诀/说明
     * @param segRules  分层规则
     * @param questions 题目 [{id, stem, star}]，stem=biz_question.stem_text 原样
     */
    public String buildPrepSegHtml(String segName, String segStyle, String segTopic,
                                   String segNote, String segRules, List<Map<String, Object>> questions) {
        boolean special = (segStyle != null && segStyle.contains("★"))
            || (segRules != null && !segRules.isBlank())
            || (segName != null && (segName.contains("专项") || segName.contains("奥数") || segName.contains("最值")));
        boolean think = segName != null && segName.contains("思维");
        boolean inner = segName != null && (segName.contains("课内") || segName.contains("同步"));
        String title = (segTopic != null && !segTopic.isBlank()) ? segTopic
            : (segName != null ? segName : "练习");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><meta charset=\"utf-8\"><title>").append(esc(title)).append("</title><style>");
        sb.append("@page{size:A4;margin:15mm 15mm 13mm}");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:\"SimSun\",\"宋体\",serif;color:#111;font-size:13.5px;line-height:1.75}");
        sb.append(".hd{text-align:center;border-bottom:2px solid #111;padding-bottom:7px;margin-bottom:6px}");
        sb.append(".hd h1{font-family:\"SimHei\",\"黑体\",sans-serif;font-size:19px;letter-spacing:.06em}");
        sb.append(".kj{border:1.5px solid #111;border-radius:6px;padding:7px 12px;margin:8px 0 4px;font-size:12.5px;line-height:1.7}");
        sb.append(".kj b{font-family:\"SimHei\",\"黑体\",sans-serif;font-weight:400}");
        sb.append(".lv{font-family:\"SimHei\",\"黑体\",sans-serif;font-size:14.5px;margin:14px 0 4px;break-after:avoid}");
        sb.append(".q{margin:0 0 6px;break-inside:avoid}");
        sb.append(".q .t b{font-family:\"SimHei\",\"黑体\",sans-serif;font-weight:400}");
        sb.append(".q img{max-width:100%}");
        sb.append(".sp{height:").append(think ? "70mm" : (inner ? "20mm" : "30mm")).append("}");
        sb.append(".sp.s{height:22mm}");
        sb.append(".foot{margin-top:8mm;text-align:center;font-size:11px;color:#666}");
        sb.append("</style>");
        sb.append("<div class=\"hd\"><h1>").append(esc(title)).append("</h1></div>");

        if (special) {
            String kj = segNote != null && !segNote.isBlank() ? segNote
                : (segRules != null ? segRules : "");
            if (!kj.isBlank()) {
                sb.append("<div class=\"kj\"><b>核心口诀</b>　").append(esc(kj)).append("</div>");
            }
            appendStarLayers(sb, questions);
        } else {
            int n = 1;
            for (Map<String, Object> q : questions) {
                sb.append(qHtml(n++, stem(q), inner));
            }
        }
        sb.append("<div class=\"foot\">— 完 —</div>");
        return sb.toString();
    }

    private void appendStarLayers(StringBuilder sb, List<Map<String, Object>> questions) {
        String[] heads = {
            "第一层 ★ 基础练习",
            "第二层 ★★ 挑战进阶",
            "第三层 ★★★ 压轴挑战（选做，能想到哪一步写到哪一步）"
        };
        int n = 1;
        for (int layer = 1; layer <= 3; layer++) {
            final int lv = layer;
            List<Map<String, Object>> group = questions.stream()
                .filter(q -> starLevel(q) == lv).toList();
            if (group.isEmpty()) continue;
            sb.append("<div class=\"lv\">").append(heads[layer - 1]).append("</div>");
            for (Map<String, Object> q : group) {
                sb.append(qHtml(n++, stem(q), false));
            }
        }
    }

    /** 星级：'2'/'3' → 2/3；其余（'1'/null/空）归第一层。 */
    private int starLevel(Map<String, Object> q) {
        Object s = q.get("star");
        if (s == null) return 1;
        String v = String.valueOf(s).trim();
        if ("2".equals(v)) return 2;
        if ("3".equals(v)) return 3;
        return 1;
    }

    private String stem(Map<String, Object> q) {
        Object s = q.get("stem");
        return s == null ? "" : String.valueOf(s);
    }

    private String qHtml(int n, String stemHtml, boolean small) {
        return "<div class=\"q\"><div class=\"t\"><b>" + n + ".</b>　" + stemHtml + "</div>"
            + "<div class=\"sp" + (small ? " s" : "") + "\"></div></div>";
    }

    /**
     * 家长版两列长图 HTML（对照 06）。🔴 内部字段（素材源/思维动作/层数/星级/prep态/题数/肖像/吃透课编号）不出现。
     * 行文案：思维题→"思维热身"固定；专项→parent_copy；课内→segTemplate[2].topic。测试课=琥珀底。
     */
    public String buildParentHtml(String targetName, String subject, BizCoursePlan plan,
                                  List<BizCoursePlanLesson> lessons,
                                  Map<Long, BizScheduleSession> lessonToSession) {
        int total = lessons.size();
        int leftCount = (total + 1) / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><meta charset=\"utf-8\"><title>").append(esc(targetName)).append(" · 课程安排</title><style>");
        sb.append(":root{--teal:#0f766e;--deep:#0b5d56;--soft:#e6f3f1;--ink:#22372f;--sub:#5d6f6a;--faint:#90a29d;--bd:#d4e0dc;--amber:#9a5b12;--amber-soft:#fdf3e3}");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:\"Microsoft YaHei\",\"PingFang SC\",sans-serif;color:var(--ink);background:#fff;font-size:13px;line-height:1.55;width:900px}");
        sb.append(".page{padding:22px 22px 16px}");
        sb.append(".hd{display:flex;align-items:baseline;justify-content:space-between;gap:10px;padding-bottom:10px;border-bottom:2.5px solid var(--teal);margin-bottom:12px}");
        sb.append(".hd h1{font-size:19px;font-weight:800}");
        sb.append(".hd .m{font-size:12px;color:var(--sub);text-align:right;line-height:1.6}");
        sb.append(".hd .m b{color:var(--deep)}");
        sb.append(".sub{font-size:11.5px;color:var(--faint);margin-bottom:10px}");
        sb.append(".cols{display:grid;grid-template-columns:1fr 1fr;gap:12px;align-items:start}");
        sb.append(".ls{border:1px solid var(--bd);border-radius:10px;overflow:hidden}");
        sb.append(".row{display:flex;border-top:1px solid var(--bd)}");
        sb.append(".row:first-child{border-top:none}");
        sb.append(".row:nth-child(even){background:#fafdfc}");
        sb.append(".dt{flex:none;width:64px;padding:8px 6px 8px 10px;border-right:1px solid var(--bd);display:flex;flex-direction:column;justify-content:center}");
        sb.append(".dt b{font-size:13.5px}");
        sb.append(".dt span{font-size:10px;color:var(--faint);line-height:1.45}");
        sb.append(".ct{flex:1;padding:7px 10px 7px 9px;display:flex;flex-direction:column;gap:2px}");
        sb.append(".seg{display:flex;gap:6px;align-items:baseline}");
        sb.append(".k{flex:none;font-size:10px;font-weight:700;border-radius:4px;padding:0 5px;line-height:16px}");
        sb.append(".k.w{color:var(--deep);background:var(--soft)}");
        sb.append(".k.m{color:#fff;background:var(--teal)}");
        sb.append(".k.s{color:var(--sub);background:#edf1ef}");
        sb.append(".seg .x{font-size:12px;line-height:1.45}");
        sb.append(".row.test{background:var(--amber-soft)}");
        sb.append(".row.test .tt{font-size:12.5px;font-weight:800;color:var(--amber)}");
        sb.append(".row.test .ts{font-size:11px;color:var(--amber);opacity:.85}");
        sb.append(".ft{margin-top:10px;font-size:11px;color:var(--faint);text-align:center}");
        sb.append("</style>");

        sb.append("<div class=\"page\">");
        sb.append("<div class=\"hd\"><h1>").append(esc(targetName)).append(" · 课程安排</h1>");
        String meta = (plan.getYear() != null ? plan.getYear() + " " : "")
            + (plan.getTermTag() != null ? plan.getTermTag() : "")
            + " · 共 " + total + " 次";
        sb.append("<div class=\"m\"><b>").append(esc(meta.trim())).append("</b></div></div>");
        sb.append("<div class=\"sub\">每次课三段：思维题 → 奥数专项 → 课内同步</div>");
        sb.append("<div class=\"cols\">");
        sb.append("<div class=\"ls\">");
        for (int i = 0; i < total; i++) {
            if (i == leftCount) sb.append("</div><div class=\"ls\">");
            sb.append(rowHtml(lessons.get(i), lessonToSession));
        }
        sb.append("</div></div>");
        String ftSubject = subject != null ? subject : "课程";
        sb.append("<div class=\"ft\">").append(esc(ftSubject)).append(" · 课程安排</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String rowHtml(BizCoursePlanLesson l, Map<Long, BizScheduleSession> lessonToSession) {
        boolean test = "1".equals(l.getLessonType());
        BizScheduleSession s = lessonToSession.get(l.getId());
        String dateStr = "—";
        String weekTime = "";
        if (s != null && s.getSessionDate() != null) {
            LocalDate d = s.getSessionDate();
            dateStr = d.getMonthValue() + "/" + d.getDayOfMonth();
            weekTime = weekday(d) + (s.getStartTime() != null ? " " + trimSec(s.getStartTime()) : "");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"row").append(test ? " test" : "").append("\">");
        sb.append("<div class=\"dt\"><span>第 ").append(l.getLessonSeq()).append(" 次</span><b>")
            .append(dateStr).append("</b><span>").append(esc(weekTime)).append("</span></div>");
        sb.append("<div class=\"ct\">");
        if (test) {
            sb.append("<div class=\"tt\">🧪 ").append(esc(l.getTitle() == null ? "测试" : l.getTitle())).append("</div>");
            sb.append("<div class=\"ts\">阶段综合检测</div>");
        } else {
            // 思维题固定文案
            sb.append("<div class=\"seg\"><span class=\"k w\">思维题</span><span class=\"x\">思维热身</span></div>");
            // 专项 = parent_copy
            String zx = l.getParentCopy() != null && !l.getParentCopy().isBlank()
                ? l.getParentCopy() : (l.getTitle() != null ? l.getTitle() : "专项练习");
            sb.append("<div class=\"seg\"><span class=\"k m\">专项</span><span class=\"x\">").append(esc(zx)).append("</span></div>");
            // 课内 = segTemplate[2].topic
            String inner = innerTopic(l.getSegTemplate());
            if (inner != null && !inner.isBlank()) {
                sb.append("<div class=\"seg\"><span class=\"k s\">课内</span><span class=\"x\">").append(esc(inner)).append("</span></div>");
            }
        }
        sb.append("</div></div>");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String innerTopic(String segTemplateJson) {
        if (segTemplateJson == null || segTemplateJson.isBlank()) return null;
        try {
            List<Object> arr = JsonUtils.parseArray(segTemplateJson, Object.class);
            if (arr != null && arr.size() >= 3 && arr.get(2) instanceof Map<?, ?> seg) {
                Object t = ((Map<String, Object>) seg).get("topic");
                return t == null ? null : String.valueOf(t);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private String trimSec(String t) {
        if (t != null && t.length() >= 5) return t.substring(0, 5);
        return t;
    }

    private String weekday(LocalDate d) {
        String[] w = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return w[d.getDayOfWeek().getValue() - 1];
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
