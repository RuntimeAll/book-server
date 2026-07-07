package org.dromara.book.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 教学安排渲染工具（PRD-C-213）：HTML 模板拼装 + <b>纯 Java 进程内</b>出 PDF/PNG。
 *
 * <p>BUG-010 重构（2026-07-05 拍板）：彻底去浏览器——openhtmltopdf(pdfbox) 进程内渲染，
 * 不再拉本机 Edge（Windows 黑窗 / 每段冷启 / prod 装浏览器三债一次还清）。
 * 备课材料三段拼一份 HTML、段间强制起新页，一次出<b>一份 PDF</b>；家长版 PNG =
 * HTML→PDF→pdfbox 光栅化。页数统计 = pdfbox {@code getNumberOfPages()}。
 *
 * <p>中文字体不打包进 jar（体积）：yml {@code schedule.pdf.font-main / font-heading}
 * 覆盖口 + 缺省探测链（Windows 探 C:\Windows\Fonts 纯 TTF；Linux 探 /usr/share/fonts
 * 常见 Noto/文泉驿/黑体路径）；全 miss 抛 ServiceException 清晰报错。
 * ⚠️ .ttc 集合字体 openhtmltopdf useFont 不支持，探测链一律跳过（simsun.ttc 等）；
 * CSS font-family 全部落到已注册字体族（'cjk' 正文 / 'cjkhei' 标题）——版式近似口径。
 *
 * <p>🔴 PDF 只有题目，任何答案/解析字段不得进 HTML；家长版内部字段一律不出现。
 *
 * @author backend-dev
 */
@Slf4j
@Component
public class ScheduleRenderUtil {

    /** CSS 里唯一认的两个逻辑字体族：正文 / 标题（黑体系）。 */
    private static final String FAMILY_MAIN = "cjk";
    private static final String FAMILY_HEAD = "cjkhei";

    /** Windows 探测链——只收纯 TTF（simhei 最稳）；simsun.ttc 为集合字体不支持，不入链。 */
    private static final String[] WIN_FONT_PROBE = {
        "C:\\Windows\\Fonts\\simhei.ttf",
        "C:\\Windows\\Fonts\\simkai.ttf",
        "C:\\Windows\\Fonts\\simfang.ttf"
    };

    /** Linux 探测链——常见 Noto CJK(otf) / 文泉驿 / 手工拷贝黑体路径（.ttc 不收）。 */
    private static final String[] LINUX_FONT_PROBE = {
        "/usr/share/fonts/simhei.ttf",
        "/usr/share/fonts/chinese/simhei.ttf",
        "/usr/share/fonts/truetype/simhei.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf",
        "/usr/share/fonts/noto/NotoSansCJKsc-Regular.otf",
        "/usr/share/fonts/opentype/noto/NotoSansSC-Regular.otf",
        "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.otf",
        "/usr/share/fonts/wenquanyi/wqy-microhei/wqy-microhei.ttf",
        "/usr/share/fonts/wqy-microhei/wqy-microhei.ttf"
    };

    @Value("${schedule.pdf.font-main:}")
    private String fontMainPath;

    @Value("${schedule.pdf.font-heading:}")
    private String fontHeadingPath;

    @Value("${schedule.artifact-dir:./prep-artifacts}")
    private String artifactDir;

    /** 解析后的字体缓存 [main, heading]（进程内探测一次）。 */
    private volatile File[] resolvedFonts;

    @Getter
    public static class PdfResult {
        private final String file;
        private final int pages;
        public PdfResult(String file, int pages) { this.file = file; this.pages = pages; }
    }

    /** 段渲染数据（BUG-004：groups 可选段内分组；无 groups 走 questions 平铺/星级）。 */
    @Data
    public static class SegData {
        private String name;
        private String style;
        private String topic;
        private String note;
        private String rules;
        /** 题目 [{id, stem, star, qtype}]，stem=biz_question.stem_text 原样。groups 非空时忽略。 */
        private List<Map<String, Object>> questions = new ArrayList<>();
        /** 可选段内分组（灰阶小标题），对照原版「基础过关（易错向）」式。 */
        private List<SegGroup> groups;
    }

    @Data
    public static class SegGroup {
        private String title;
        private List<Map<String, Object>> questions = new ArrayList<>();
    }

    // ─────────────────────────── 产物目录 ───────────────────────────

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

    // ─────────────────────────── 字体探测 ───────────────────────────

    /** 解析正文/标题字体（yml 覆盖口优先，缺省走平台探测链；全 miss 抛清晰报错）。 */
    private File[] fonts() {
        File[] cached = resolvedFonts;
        if (cached != null) return cached;
        synchronized (this) {
            if (resolvedFonts != null) return resolvedFonts;
            File main = fromOverride(fontMainPath, "schedule.pdf.font-main");
            if (main == null) main = probeFont();
            if (main == null) {
                throw new ServiceException("未找到可用中文字体：Windows 需 C:\\Windows\\Fonts\\simhei.ttf 等纯 TTF；"
                    + "Linux 需安装 Noto CJK(otf)/文泉驿或拷贝 simhei.ttf 到 /usr/share/fonts；"
                    + "也可在 yml 配置 schedule.pdf.font-main 指向字体文件（.ttc 集合字体不支持）");
            }
            File heading = fromOverride(fontHeadingPath, "schedule.pdf.font-heading");
            if (heading == null) heading = main;
            log.info("[schedule-render] 字体注册：main={} heading={}", main, heading);
            resolvedFonts = new File[]{main, heading};
            return resolvedFonts;
        }
    }

    private File fromOverride(String path, String key) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path.trim());
        if (!f.exists() || !f.isFile()) {
            throw new ServiceException("yml " + key + " 指向的字体文件不存在：" + path);
        }
        if (isTtc(f)) {
            throw new ServiceException("yml " + key + " 指向 .ttc 集合字体，openhtmltopdf 不支持，请改用纯 TTF/OTF：" + path);
        }
        return f;
    }

    private File probeFont() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String[] probe = windows ? WIN_FONT_PROBE : LINUX_FONT_PROBE;
        for (String p : probe) {
            File f = new File(p);
            if (!f.exists() || !f.isFile()) continue;
            if (isTtc(f)) {
                log.warn("[schedule-render] 跳过 .ttc 集合字体（不支持）：{}", p);
                continue;
            }
            return f;
        }
        return null;
    }

    private boolean isTtc(File f) {
        return f.getName().toLowerCase(Locale.ROOT).endsWith(".ttc");
    }

    // ─────────────────────────── 核心渲染（纯 Java） ───────────────────────────

    /** HTML（宽松）→ jsoup 规整 W3C DOM → openhtmltopdf → PDF bytes。 */
    private byte[] htmlToPdfBytes(String html) {
        try {
            File[] f = fonts();
            org.w3c.dom.Document w3c = new W3CDom().fromJsoup(Jsoup.parse(html));
            ByteArrayOutputStream os = new ByteArrayOutputStream(1 << 20);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(f[0], FAMILY_MAIN);
            builder.useFont(f[1], FAMILY_HEAD);
            builder.withW3cDocument(w3c, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("PDF 渲染异常：" + e.getMessage());
        }
    }

    /** HTML → 一份 PDF 落产物目录。返回相对文件名 + 真页数（pdfbox getNumberOfPages）。 */
    public PdfResult renderPdf(String html, String name) {
        Path root = artifactRoot();
        String base = safe(name);
        Path pdfFile = root.resolve(base + ".pdf");
        try {
            // 留一份 HTML 便于人工对拍样张（非契约产物）
            Files.writeString(root.resolve(base + ".html"), html, StandardCharsets.UTF_8);
            byte[] bytes = htmlToPdfBytes(html);
            if (bytes.length == 0) {
                throw new ServiceException("PDF 生成失败（产物为空）：" + base);
            }
            Files.write(pdfFile, bytes);
            int pages;
            try (PDDocument doc = PDDocument.load(bytes)) {
                pages = doc.getNumberOfPages();
            }
            return new PdfResult(pdfFile.getFileName().toString(), pages);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("PDF 渲染异常：" + e.getMessage());
        }
    }

    /**
     * HTML → PNG（家长版长图，BUG-010 去 Edge）：注入 @page 尺寸（width×height px、零边距）
     * → 进程内出 PDF → pdfbox 光栅化第 1 页。192dpi = CSS px 布局不变、位图 2 倍保清晰。
     */
    public String renderToPng(String html, String name, int width, int height) {
        Path root = artifactRoot();
        String base = safe(name);
        Path pngFile = root.resolve(base + ".png");
        try {
            org.jsoup.nodes.Document jdoc = Jsoup.parse(html);
            jdoc.head().append("<style>@page{size:" + width + "px " + height + "px;margin:0}</style>");
            byte[] pdf = htmlToPdfBytes(jdoc.outerHtml());
            try (PDDocument doc = PDDocument.load(pdf)) {
                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage img = renderer.renderImageWithDPI(0, 192f);
                if (!ImageIO.write(img, "png", pngFile.toFile())) {
                    throw new ServiceException("PNG 编码失败：" + base);
                }
            }
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

    private String safe(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // ─────────────────────────── 备课材料整包 HTML ───────────────────────────

    /**
     * 备课材料全段拼一份 HTML（对照 09a/b/c；BUG-010 单文件拍板）：段间强制起新页。
     * 专项段（style/rules 真含层级标记）= 核心口诀 kj 框 + 三层星级；
     * 普通段带 groups = 灰阶小标题分组（BUG-004）；否则平铺题列。
     * 留白分档（BUG-004）：按题型 选择/判断 20mm、填空 22mm、计算/解答 30mm；
     * 思维段整段 70mm；题型缺失退回段级现值（课内 20mm / 其余 30mm）。
     * 🔴 仅题目无答案解析。
     */
    public String buildPrepPackHtml(List<SegData> segs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><title>备课材料</title><style>");
        // 🔴 字号面向小学四年级放大（2026-07-06 维护者反馈）：正文 17px、标题 24px、行高 1.9。
        sb.append("@page{size:A4;margin:16mm 15mm 16mm}");
        // 页脚：底部居中页码（2026-07-07 维护者要求；按层分页 = 每段 page-break-before 起新页）
        sb.append("@page{@bottom-center{content:\"— 第 \" counter(page) \" 页 —\";font-family:'")
            .append(FAMILY_MAIN).append("';font-size:10px;color:#999}}");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:'").append(FAMILY_MAIN).append("';color:#111;font-size:17px;line-height:1.9}");
        sb.append(".seg{page-break-before:always}");
        sb.append(".seg.first{page-break-before:auto}");
        sb.append(".hd{text-align:center;border-bottom:2px solid #111;padding-bottom:8px;margin-bottom:8px}");
        sb.append(".hd h1{font-family:'").append(FAMILY_HEAD).append("';font-size:24px;letter-spacing:.06em;font-weight:normal}");
        sb.append(".kj{border:1.5px solid #111;border-radius:6px;padding:8px 13px;margin:8px 0 4px;font-size:15px;line-height:1.75}");
        sb.append(".kj b{font-family:'").append(FAMILY_HEAD).append("';font-weight:normal}");
        sb.append(".sub{font-size:15px;color:#444;margin:6px 0 8px}");
        sb.append(".lv{font-family:'").append(FAMILY_HEAD).append("';font-size:18px;margin:16px 0 5px;page-break-after:avoid}");
        sb.append(".grp{font-family:'").append(FAMILY_HEAD).append("';font-size:15px;color:#555;")
            .append("background:#f0f0f0;border-left:3px solid #9a9a9a;padding:3px 9px;margin:13px 0 7px;page-break-after:avoid}");
        sb.append(".q{margin:0 0 9px;page-break-inside:avoid}");
        sb.append(".q .t b{font-family:'").append(FAMILY_HEAD).append("';font-weight:normal}");
        sb.append(".star{color:#e8912a;font-size:14px;letter-spacing:1px}");
        sb.append(".q img{max-width:100%}");
        sb.append(".foot{margin-top:8mm;text-align:center;font-size:12px;color:#666}");
        sb.append("</style></head><body>");
        for (int i = 0; i < segs.size(); i++) {
            appendSeg(sb, segs.get(i), i == 0);
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendSeg(StringBuilder sb, SegData seg, boolean first) {
        String name = seg.getName();
        boolean think = name != null && name.contains("思维");
        boolean inner = name != null && (name.contains("课内") || name.contains("同步"));
        boolean grouped = seg.getGroups() != null && !seg.getGroups().isEmpty();
        // 🔴 D12：仅当 style/rules 真含层级标记（★ 或「第[一二三]层」）才走口诀+三层版式；
        // 带 groups 的段优先分组版式（BUG-004 自定义两层结构）。
        boolean special = !grouped && (hasLayerMark(seg.getStyle()) || hasLayerMark(seg.getRules()));
        String title = seg.getTopic() != null && !seg.getTopic().isBlank() ? seg.getTopic()
            : (name != null ? name : "练习");

        sb.append("<div class=\"seg").append(first ? " first" : "").append("\">");
        sb.append("<div class=\"hd\"><h1>").append(esc(title)).append("</h1></div>");

        if (special) {
            String kj = seg.getNote() != null && !seg.getNote().isBlank() ? seg.getNote()
                : (seg.getRules() != null ? seg.getRules() : "");
            if (!kj.isBlank()) {
                sb.append("<div class=\"kj\"><b>核心口诀</b>　").append(esc(kj)).append("</div>");
            }
            appendStarLayers(sb, seg.getQuestions(), think, inner);
        } else {
            // 普通段：note/rules 作副标题文案，不出口诀框、不出层标题。
            String subtitle = seg.getNote() != null && !seg.getNote().isBlank() ? seg.getNote() : seg.getRules();
            if (subtitle != null && !subtitle.isBlank()) {
                sb.append("<div class=\"sub\">").append(esc(subtitle)).append("</div>");
            }
            int n = 1;
            if (grouped) {
                // BUG-004：段内分组灰阶小标题（对照原版「基础过关（易错向）」式），题号跨组连续
                for (SegGroup g : seg.getGroups()) {
                    if (g.getTitle() != null && !g.getTitle().isBlank()) {
                        sb.append("<div class=\"grp\">").append(esc(g.getTitle())).append("</div>");
                    }
                    for (Map<String, Object> q : g.getQuestions()) {
                        sb.append(qHtml(n++, q, think, inner));
                    }
                }
            } else {
                for (Map<String, Object> q : seg.getQuestions()) {
                    sb.append(qHtml(n++, q, think, inner));
                }
            }
        }
        sb.append("</div>");
    }

    private void appendStarLayers(StringBuilder sb, List<Map<String, Object>> questions,
                                  boolean think, boolean inner) {
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
                sb.append(qHtml(n++, q, think, inner));
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

    private String qHtml(int n, Map<String, Object> q, boolean think, boolean inner) {
        return "<div class=\"q\"><div class=\"t\"><b>" + n + ".</b>" + starMark(q) + "　" + stem(q) + "</div>"
            + "<div style=\"height:" + spacerHeight(q, think, inner) + "\"></div></div>";
    }

    /** 题号后难度星：q.star 非空且&gt;0 时输出 ★×lv（专项段每题标难度；star=biz_question.star_level，NULL 不显示）。 */
    private String starMark(Map<String, Object> q) {
        Object s = q.get("star");
        if (s == null) return "";
        int lv;
        try { lv = Integer.parseInt(String.valueOf(s).trim()); } catch (Exception e) { return ""; }
        if (lv <= 0) return "";
        StringBuilder b = new StringBuilder("　<span class=\"star\">");
        for (int i = 0; i < lv; i++) b.append("★");
        return b.append("</span>").toString();
    }

    /**
     * 答题留白分档（BUG-004）：思维段整段 70mm（现状口径）；否则按题型
     * 选择(1)/判断(3) 20mm、填空(2/4) 22mm、计算/解答(5) 30mm；
     * 题型缺失退回段级现值（课内 20mm / 其余 30mm）。
     */
    private String spacerHeight(Map<String, Object> q, boolean think, boolean inner) {
        if (think) return "70mm";
        Object t = q.get("qtype");
        String v = t == null ? "" : String.valueOf(t).trim();
        return switch (v) {
            case "1", "3" -> "20mm";
            case "2", "4" -> "22mm";
            case "5" -> "30mm";
            default -> inner ? "20mm" : "30mm";
        };
    }

    // ─────────────────────────── 家长版长图 HTML ───────────────────────────

    /**
     * 家长版两列长图 HTML（对照 06）。🔴 内部字段（素材源/思维动作/层数/星级/prep态/题数/肖像/吃透课编号）不出现。
     * 行文案：思维题→"思维热身"固定；专项→parent_copy；课内→segTemplate[2].topic。测试课=琥珀底。
     * ⚠️ openhtmltopdf 不支持 flex/grid/CSS 变量——版式用 table 实现（BUG-010 去浏览器化重排，视觉近似原版）。
     */
    public String buildParentHtml(String targetName, String subject, BizCoursePlan plan,
                                  List<BizCoursePlanLesson> lessons,
                                  Map<Long, BizScheduleSession> lessonToSession) {
        int total = lessons.size();
        int leftCount = (total + 1) / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><title>")
            .append(esc(targetName)).append(" · 课程安排</title><style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:'").append(FAMILY_HEAD).append("','").append(FAMILY_MAIN)
            .append("';color:#22372f;background:#fff;font-size:13px;line-height:1.55;width:900px}");
        sb.append(".page{padding:22px 22px 16px}");
        sb.append("table{border-collapse:collapse}");
        sb.append(".hdt{width:100%;border-bottom:2.5px solid #0f766e;margin-bottom:12px}");
        sb.append(".hdt h1{font-size:19px;font-weight:bold;padding-bottom:10px}");
        sb.append(".hdt td.m{font-size:12px;color:#5d6f6a;text-align:right;vertical-align:bottom;padding-bottom:10px;line-height:1.6}");
        sb.append(".hdt td.m b{color:#0b5d56}");
        sb.append(".sub{font-size:11.5px;color:#90a29d;margin-bottom:10px}");
        sb.append(".cols{width:100%;table-layout:fixed}");
        sb.append(".cols td.colc{width:50%;vertical-align:top;padding:0 6px}");
        sb.append(".ls{width:100%;border:1px solid #d4e0dc;border-radius:10px}");
        sb.append(".ls td{border-top:1px solid #d4e0dc}");
        sb.append(".ls tr.r0 td{border-top:none}");
        sb.append(".ls tr.alt td{background:#fafdfc}");
        sb.append(".ls tr.test td{background:#fdf3e3}");
        sb.append("td.dt{width:64px;padding:8px 6px 8px 10px;border-right:1px solid #d4e0dc;vertical-align:middle}");
        sb.append(".dt .sq{display:block;font-size:10px;color:#90a29d;line-height:1.45}");
        sb.append(".dt b{font-size:13.5px}");
        sb.append("td.ct{padding:7px 10px 7px 9px;vertical-align:middle}");
        sb.append(".seg{margin:1px 0}");
        sb.append(".k{font-size:10px;font-weight:bold;border-radius:4px;padding:0 5px;line-height:16px;display:inline-block}");
        sb.append(".k.w{color:#0b5d56;background:#e6f3f1}");
        sb.append(".k.m{color:#fff;background:#0f766e}");
        sb.append(".k.s{color:#5d6f6a;background:#edf1ef}");
        sb.append(".x{font-size:12px;line-height:1.45}");
        sb.append(".tt{font-size:12.5px;font-weight:bold;color:#9a5b12}");
        sb.append(".ts{font-size:11px;color:#9a5b12}");
        sb.append(".ft{margin-top:10px;font-size:11px;color:#90a29d;text-align:center}");
        sb.append("</style></head><body>");

        sb.append("<div class=\"page\">");
        sb.append("<table class=\"hdt\"><tr><td><h1>").append(esc(targetName)).append(" · 课程安排</h1></td>");
        String meta = (plan.getYear() != null ? plan.getYear() + " " : "")
            + (plan.getTermTag() != null ? plan.getTermTag() : "")
            + " · 共 " + total + " 次";
        sb.append("<td class=\"m\"><b>").append(esc(meta.trim())).append("</b></td></tr></table>");
        sb.append("<div class=\"sub\">每次课三段：思维题 → 奥数专项 → 课内同步</div>");
        sb.append("<table class=\"cols\"><tr><td class=\"colc\"><table class=\"ls\">");
        for (int i = 0; i < total; i++) {
            if (i == leftCount) sb.append("</table></td><td class=\"colc\"><table class=\"ls\">");
            int inCol = i < leftCount ? i : i - leftCount;
            sb.append(rowHtml(lessons.get(i), lessonToSession, inCol));
        }
        sb.append("</table></td></tr></table>");
        String ftSubject = subject != null ? subject : "课程";
        sb.append("<div class=\"ft\">").append(esc(ftSubject)).append(" · 课程安排</div>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String rowHtml(BizCoursePlanLesson l, Map<Long, BizScheduleSession> lessonToSession, int inColIndex) {
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
        sb.append("<tr class=\"row");
        if (inColIndex == 0) sb.append(" r0");
        if (inColIndex % 2 == 1) sb.append(" alt");
        if (test) sb.append(" test");
        sb.append("\">");
        sb.append("<td class=\"dt\"><span class=\"sq\">第 ").append(l.getLessonSeq()).append(" 次</span><b>")
            .append(dateStr).append("</b><span class=\"sq\">").append(esc(weekTime)).append("</span></td>");
        sb.append("<td class=\"ct\">");
        if (test) {
            // ⚠️ 原版 🧪 emoji 去除：系统中文字体无 emoji 字形（openhtmltopdf 缺字形出豆腐块）
            sb.append("<div class=\"tt\">").append(esc(l.getTitle() == null ? "测试" : l.getTitle())).append("</div>");
            sb.append("<div class=\"ts\">阶段综合检测</div>");
        } else {
            // 思维题固定文案
            sb.append("<div class=\"seg\"><span class=\"k w\">思维题</span> <span class=\"x\">思维热身</span></div>");
            // 专项 = parent_copy（家长产物：过内部词防线）
            String zx = l.getParentCopy() != null && !l.getParentCopy().isBlank()
                ? l.getParentCopy() : (l.getTitle() != null ? l.getTitle() : "专项练习");
            zx = stripInternalWords(zx);
            sb.append("<div class=\"seg\"><span class=\"k m\">专项</span> <span class=\"x\">").append(esc(zx)).append("</span></div>");
            // 课内 = segTemplate[2].topic（家长产物：过内部词防线）
            String inner = stripInternalWords(innerTopic(l.getSegTemplate()));
            if (inner != null && !inner.isBlank()) {
                sb.append("<div class=\"seg\"><span class=\"k s\">课内</span> <span class=\"x\">").append(esc(inner)).append("</span></div>");
            }
        }
        sb.append("</td></tr>");
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

    /** style/rules 是否真含层级标记（★ 或「第[一二三]层」）—— D12 专项卷判据。 */
    private boolean hasLayerMark(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        return s.contains("★") || Pattern.compile("第[一二三]层").matcher(s).find();
    }

    /** 家长产物内部词防线（family = ★/第N层/N层/素材/挑题/薄弱），确定性剥离。宁可钝不可泄。 */
    private static final Pattern INTERNAL_WORD = Pattern.compile(
        "★+|第[一二三四]层|[0-9一二三四]+层|素材[源池]?|挑题|薄弱[项点环]?");

    /** 剥离内部词并压掉多余空白/空括号；供家长消息与家长版长图输出前统一过闸。 */
    public static String stripInternalWords(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        String out = INTERNAL_WORD.matcher(s).replaceAll("");
        // 清理剥离后遗留的空括号 / 连续标点 / 多余空白
        out = out.replaceAll("[（(]\\s*[)）]", "")
            .replaceAll("[ \\t]{2,}", " ")
            .replaceAll("[·、,，]{2,}", "·")
            .trim();
        return out;
    }
}
