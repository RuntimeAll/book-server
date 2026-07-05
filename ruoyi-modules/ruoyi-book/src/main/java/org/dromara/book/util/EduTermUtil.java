package org.dromara.book.util;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * 年级/学段推导工具（PRD-C-213 修复轮 R1a·BUG-001）。
 *
 * <p>🔴 建模拍板：年级/学段/册次是【推导状态】不落列。档案只存
 * {@code grade_no(1-12) + grade_year(学年锚) + textbook_edition(字典码)}，
 * 展示串（"三年级·暑假"、"人教版四年级上册"）全部由本类按当前日期推导。
 *
 * <p><b>grade_year 语义</b>（R5·FAIL-2 定版：学年切换点 = 7/1，暑假归入新学年）
 * = grade_no 生效学年的起始年（学年 = 7/1 ~ 次年 6/30）。
 * 暑期录入「升四」即 gradeNo=4、gradeYear=2026（2026-07-01 起即读四年级，暑假=新学年预习期）；
 * 当前年级 = gradeNo + (当前学年起始年 - gradeYear)，7/1 进位
 * （2026-07 推导得 4 = 四年级·暑假，与 textbookLabel「暑假=新年级上册」同口径）。
 *
 * <p><b>期段边界</b>（类常量缺省，yml 覆盖口 = {@code edu.term.*}，见
 * {@link org.dromara.book.config.EduTermConfig}）：
 * 7/1-8/31 暑假、9/1-1/15 上学期、1/16-2/15 寒假、2/16-6/30 下学期。
 * 期段码与 biz_course_plan.term_tag 共用同一套字典 biz_term_tag（值=中文文本）。
 *
 * <p><b>册次推导</b>（textbookLabel）：暑假/上学期=当前年级上册（暑假期间「当前年级」已按
 * 7/1 口径进位到新年级，即预习新学年上册）；寒假/下学期=当前年级下册。
 *
 * @author backend-dev
 */
public final class EduTermUtil {

    /** 期段码（= 字典 biz_term_tag 的 dict_value，中文文本） */
    public static final String TERM_SUMMER = "暑假";
    public static final String TERM_AUTUMN = "上学期";
    public static final String TERM_WINTER = "寒假";
    public static final String TERM_SPRING = "下学期";

    /** 缺省期段边界（起始日；每段到下一段起始日前一天止） */
    public static final MonthDay DEFAULT_SUMMER_START = MonthDay.of(7, 1);
    public static final MonthDay DEFAULT_AUTUMN_START = MonthDay.of(9, 1);
    public static final MonthDay DEFAULT_WINTER_START = MonthDay.of(1, 16);
    public static final MonthDay DEFAULT_SPRING_START = MonthDay.of(2, 16);

    private static volatile MonthDay summerStart = DEFAULT_SUMMER_START;
    private static volatile MonthDay autumnStart = DEFAULT_AUTUMN_START;
    private static volatile MonthDay winterStart = DEFAULT_WINTER_START;
    private static volatile MonthDay springStart = DEFAULT_SPRING_START;

    private static final String[] GRADE_LABELS = {
        "一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
        "七年级", "八年级", "九年级", "高一", "高二", "高三"
    };

    private EduTermUtil() {
    }

    /** yml 覆盖口（EduTermConfig 启动时注入；传 null 的项保持缺省）。 */
    public static void configure(MonthDay summer, MonthDay autumn, MonthDay winter, MonthDay spring) {
        if (summer != null) summerStart = summer;
        if (autumn != null) autumnStart = autumn;
        if (winter != null) winterStart = winter;
        if (spring != null) springStart = spring;
    }

    /** 当前期段码（暑假/上学期/寒假/下学期）。 */
    public static String currentTerm(LocalDate date) {
        MonthDay md = MonthDay.from(date);
        if (!md.isBefore(summerStart) && md.isBefore(autumnStart)) {
            return TERM_SUMMER;
        }
        if (!md.isBefore(autumnStart) || md.isBefore(winterStart)) {
            return TERM_AUTUMN;
        }
        if (!md.isBefore(winterStart) && md.isBefore(springStart)) {
            return TERM_WINTER;
        }
        return TERM_SPRING;
    }

    /** 当前学年起始年（🔴 7/1 口径：7/1 前属上一学年，暑假归入新学年——与 textbookLabel「暑假=新年级」一致）。 */
    public static int schoolYearStart(LocalDate date) {
        return MonthDay.from(date).isBefore(summerStart) ? date.getYear() - 1 : date.getYear();
    }

    /**
     * 当前年级（7/1 进位）：gradeNo + (当前学年起始年 - gradeYear)，夹在 1..12。
     * gradeNo 空返回 null；gradeYear 空退化为 gradeNo 原值（无锚不进位）。
     */
    public static Integer currentGradeNo(Integer gradeNo, Integer gradeYear, LocalDate date) {
        if (gradeNo == null) {
            return null;
        }
        if (gradeYear == null) {
            return clampGrade(gradeNo);
        }
        return clampGrade(gradeNo + (schoolYearStart(date) - gradeYear));
    }

    /** 年级码 → 标签（1-9=一年级~九年级，10-12=高一/高二/高三）。 */
    public static String gradeNoLabel(Integer n) {
        if (n == null || n < 1 || n > 12) {
            return null;
        }
        return GRADE_LABELS[n - 1];
    }

    /** 显示串："三年级·暑假"（当前年级标签 + 期段）。gradeNo 空返回 null。 */
    public static String gradeLabel(Integer gradeNo, Integer gradeYear, LocalDate date) {
        Integer cur = currentGradeNo(gradeNo, gradeYear, date);
        if (cur == null) {
            return null;
        }
        return gradeNoLabel(cur) + "·" + currentTerm(date);
    }

    /**
     * 教材显示串："人教版四年级上册"（版本 + 年级 + 册次全推导）。
     * 册次规则（7/1 学年口径，currentGradeNo 已在暑假进位到新年级，此处不再 +1）：
     * 暑假/上学期→当前年级上册；寒假/下学期→当前年级下册。
     * gradeNo 空返回 null；edition 空则省略版本前缀。
     */
    public static String textbookLabel(String edition, Integer gradeNo, Integer gradeYear, LocalDate date) {
        Integer cur = currentGradeNo(gradeNo, gradeYear, date);
        if (cur == null) {
            return null;
        }
        String term = currentTerm(date);
        String volume = (TERM_SUMMER.equals(term) || TERM_AUTUMN.equals(term)) ? "上册" : "下册";
        String ed = editionLabel(edition);
        return (ed == null ? "" : ed + "版") + gradeNoLabel(cur) + volume;
    }

    /** 版本字典码 → 标签（1浙教/2人教/3北师大/4苏教）；已是中文标签则剥"版"后原样返；空返 null。 */
    public static String editionLabel(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        switch (code.trim()) {
            case "1": return "浙教";
            case "2": return "人教";
            case "3": return "北师大";
            case "4": return "苏教";
            default:
                String v = code.trim();
                return v.endsWith("版") ? v.substring(0, v.length() - 1) : v;
        }
    }

    /** 版本入参归一化 → 字典码（兼容 FE/MCP 传中文标签）。未知值原样返。 */
    public static String normalizeEdition(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim();
        if (t.endsWith("版")) {
            t = t.substring(0, t.length() - 1);
        }
        switch (t) {
            case "浙教": return "1";
            case "人教": return "2";
            case "北师大": return "3";
            case "苏教": return "4";
            default: return v.trim();
        }
    }

    /** 学科字典码 → 标签（1数学/2科学/3语文/4英语）；已是标签原样返；空返 null。 */
    public static String subjectLabel(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        switch (code.trim()) {
            case "1": return "数学";
            case "2": return "科学";
            case "3": return "语文";
            case "4": return "英语";
            default: return code.trim();
        }
    }

    /** 学科入参归一化 → 字典码（兼容 FE/MCP 传中文标签）。未知值原样返。 */
    public static String normalizeSubject(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        switch (v.trim()) {
            case "数学": return "1";
            case "科学": return "2";
            case "语文": return "3";
            case "英语": return "4";
            default: return v.trim();
        }
    }

    private static int clampGrade(int n) {
        return Math.max(1, Math.min(12, n));
    }
}
