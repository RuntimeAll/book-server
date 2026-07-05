package org.dromara.book.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-010/BUG-004 渲染重构自验（纯 Java 出 PDF/PNG，无浏览器）：
 * 假题目数据走 buildPrepPackHtml（思维段 + 专项段[口诀+三层星级] + 普通段[groups 分组]）
 * → renderPdf 出样张，pdfbox 断言：页数 ≥3（三段各起新页）、提取文本含中文题干与
 * 「核心口诀」「第一层」「基础过关」（文本可抽取 = 中文字体注册成功、非豆腐块）。
 * 样张固定落 target/prep-render-selftest/ 供人工对拍 09b。
 *
 * <p>⚠️ 依赖本机系统中文字体（Windows simhei.ttf / Linux Noto CJK），CI 无字体环境会红——
 * 与运行期字体探测行为一致，属预期。
 */
@Tag("dev")
class ScheduleRenderUtilTest {

    private static final Path OUT_DIR = Paths.get("target", "prep-render-selftest").toAbsolutePath();

    private ScheduleRenderUtil newUtil() throws Exception {
        ScheduleRenderUtil util = new ScheduleRenderUtil();
        set(util, "fontMainPath", "");
        set(util, "fontHeadingPath", "");
        set(util, "artifactDir", OUT_DIR.toString());
        return util;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Map<String, Object> q(String id, String stem, String star, String qtype) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("stem", stem);
        m.put("star", star);
        m.put("qtype", qtype);
        return m;
    }

    @Test
    void renderPrepPackPdf_threeSegsOneFile() throws Exception {
        ScheduleRenderUtil util = newUtil();

        // 段1：思维题（整段 70mm 留白口径）
        ScheduleRenderUtil.SegData s1 = new ScheduleRenderUtil.SegData();
        s1.setName("思维题");
        s1.setRules("热身 2 题");
        s1.setQuestions(List.of(
            q("1", "小明有 3 个苹果，小红比他多 2 个，两人一共有几个苹果？", null, null),
            q("2", "一个数加上 5 等于 12，这个数是多少？", null, null)));

        // 段2：奥数专项（口诀 + 三层星级）
        ScheduleRenderUtil.SegData s2 = new ScheduleRenderUtil.SegData();
        s2.setName("奥数专项");
        s2.setTopic("和差问题");
        s2.setStyle("三层星级 ★");
        s2.setNote("和差问题：大数 =（和 + 差）÷ 2，小数 =（和 - 差）÷ 2");
        s2.setRules("第一层基础 / 第二层进阶 / 第三层压轴");
        s2.setQuestions(List.of(
            q("11", "两数之和为 20，差为 4，求这两个数。", "1", "5"),
            q("12", "鸡兔同笼，共 10 个头 28 条腿，鸡兔各几只？", "2", "5"),
            q("13", "甲乙两数之和是 100，甲比乙的 3 倍还多 4，求甲乙。", "3", "5")));

        // 段3：课内同步（BUG-004 groups 分组 + 题型留白分档）
        ScheduleRenderUtil.SegData s3 = new ScheduleRenderUtil.SegData();
        s3.setName("课内同步");
        s3.setTopic("大数的认识");
        ScheduleRenderUtil.SegGroup g1 = new ScheduleRenderUtil.SegGroup();
        g1.setTitle("基础过关（易错向）");
        g1.setQuestions(List.of(
            q("21", "下列各数中，最接近十万的是（　）。A. 99999　B. 100100　C. 98000", null, "1"),
            q("22", "由 5 个十万、3 个千和 6 个一组成的数写作＿＿＿。", null, "4")));
        ScheduleRenderUtil.SegGroup g2 = new ScheduleRenderUtil.SegGroup();
        g2.setTitle("进阶·大数里的奥数");
        g2.setQuestions(List.of(
            q("23", "用 0、2、5、8 组成的最大四位数与最小四位数的差是多少？", null, "5")));
        s3.setGroups(new ArrayList<>(List.of(g1, g2)));

        String html = util.buildPrepPackHtml(List.of(s1, s2, s3));
        ScheduleRenderUtil.PdfResult res = util.renderPdf(html, "prep_selftest");
        Path pdf = OUT_DIR.resolve(res.getFile());
        System.out.println("[selftest] 样张 PDF = " + pdf);

        assertTrue(Files.exists(pdf) && Files.size(pdf) > 0, "样张 PDF 应生成且非空");
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 3,
                "三段各起新页，页数应 ≥3，实际=" + doc.getNumberOfPages());
            assertTrue(res.getPages() == doc.getNumberOfPages(), "PdfResult.pages 应与 pdfbox 页数一致");
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("核心口诀"), "专项段应含「核心口诀」");
            assertTrue(text.contains("第一层"), "专项段应含三层星级标题「第一层」");
            assertTrue(text.contains("基础过关"), "groups 段应渲染小标题「基础过关（易错向）」");
            assertTrue(text.contains("鸡兔同笼"), "中文题干应可抽取（字体注册成功，非豆腐块）");
        }
    }

    @Test
    void renderParentPng_noBrowser() throws Exception {
        ScheduleRenderUtil util = newUtil();

        BizCoursePlan plan = new BizCoursePlan();
        plan.setYear(2026);
        plan.setTermTag("暑期");

        List<BizCoursePlanLesson> lessons = new ArrayList<>();
        BizCoursePlanLesson l1 = new BizCoursePlanLesson();
        l1.setId(1L);
        l1.setLessonSeq(1);
        l1.setLessonType("0");
        l1.setTitle("和差问题");
        l1.setParentCopy("和差问题：会用画线段图求两个数");
        lessons.add(l1);
        BizCoursePlanLesson l2 = new BizCoursePlanLesson();
        l2.setId(2L);
        l2.setLessonSeq(2);
        l2.setLessonType("1");
        l2.setTitle("阶段测试一");
        lessons.add(l2);

        Map<Long, BizScheduleSession> lessonToSession = new HashMap<>();
        String html = util.buildParentHtml("小明", "数学", plan, lessons, lessonToSession);
        String file = util.renderToPng(html, "parent_selftest", 900, 280);
        Path png = OUT_DIR.resolve(file);
        System.out.println("[selftest] 样张 PNG = " + png);
        assertTrue(Files.exists(png) && Files.size(png) > 0, "家长版 PNG 应生成且非空");

        javax.imageio.ImageIO.setUseCache(false);
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new File(png.toString()));
        assertTrue(img != null && img.getWidth() == 1800,
            "192dpi 光栅化宽度应为 1800px（900px 布局 × 2），实际=" + (img == null ? "null" : img.getWidth()));
    }
}
