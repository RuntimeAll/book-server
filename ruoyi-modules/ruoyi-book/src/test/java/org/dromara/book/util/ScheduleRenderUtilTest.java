package org.dromara.book.util;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-010 渲染重构自验（纯 Java 出 PNG，无浏览器）：家长版长图 buildParentHtml → renderToPng，
 * 断言产物非空且 192dpi 光栅化宽度为 1800px（900px 布局 × 2）。
 * 样张固定落 target/prep-render-selftest/ 供人工对拍。
 *
 * <p>PRD-B-101：备课包纯 Java PDF 渲染链路已退役，buildPrepPackHtml/renderPdf 自验随之移除；
 * 仅保留家长图 PNG 光栅化路径的自验。
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
