package org.dromara.book.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 计算题出题器（口算/笔算横式，覆盖人教版小学 1-6 年级计算谱系）。
 *
 * <p>确定性程序生成，不走 LLM：每个类型一个生成器（数域/进退位/整除/去重等约束内置），
 * 按 {@code groups:[{type,count}]} 组卷 → oralcalc-v1 主题（多栏算式）→ 无头 Chrome 双卷
 * PDF（题目卷 + 教师答案卷）→ OSS。{@code seed} 给定时同参数复现同一份卷。
 *
 * <p>类型谱系对齐同步讲义（一上 5 以内起步 → 六年级分数百分比比例），卷面只出现干净的
 * 类型名（如"20以内进位加法"），无任何内部词。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OralCalcService {

    private final ChromePdfRenderer renderer;

    private static final String THEME = "oralcalc-v1";
    private static final int MAX_TOTAL = 200;
    private static final int MAX_TRY = 400;

    /** 类型定义：code / 卷面名 / 年级 / 学期(1上2下) / 每行栏数。 */
    public record CalcType(String code, String name, int grade, int term, int cols) {}

    private static final List<CalcType> TYPES = List.of(
        // ── 一年级 ──
        new CalcType("add5",       "5以内加法",             1, 1, 4),
        new CalcType("sub5",       "5以内减法",             1, 1, 4),
        new CalcType("addsub10",   "10以内加减法",          1, 1, 4),
        new CalcType("mix10",      "10以内连加连减混合",     1, 1, 3),
        new CalcType("addsub20nc", "20以内不进退位加减",     1, 1, 4),
        new CalcType("add20c",     "20以内进位加法",         1, 1, 4),
        new CalcType("sub20b",     "20以内退位减法",         1, 2, 4),
        new CalcType("addsub20",   "20以内进退位加减混合",   1, 2, 4),
        new CalcType("tens",       "整十数加减",             1, 2, 4),
        new CalcType("t2d1d",      "两位数加减一位数(不进退位)", 1, 2, 4),
        new CalcType("t2d1dc",     "两位数加减一位数(进退位)",   1, 2, 4),
        new CalcType("t2dtens",    "两位数加减整十数",       1, 2, 4),
        // ── 二年级 ──
        new CalcType("add2d2d",    "两位数加两位数",         2, 1, 4),
        new CalcType("sub2d2d",    "两位数减两位数",         2, 1, 4),
        new CalcType("mix100",     "100以内连加连减混合",    2, 1, 3),
        new CalcType("multable",   "表内乘法",               2, 1, 4),
        new CalcType("divtable",   "表内除法",               2, 2, 4),
        new CalcType("muladd",     "乘加乘减",               2, 2, 3),
        new CalcType("divrem",     "有余数的除法",           2, 2, 4),
        new CalcType("paren",      "带小括号的混合运算",      2, 2, 3),
        // ── 三年级 ──
        new CalcType("add3d",      "三位数加减法",           3, 1, 3),
        new CalcType("mul1doral",  "整十整百数乘一位数口算",  3, 1, 4),
        new CalcType("mul1d",      "多位数乘一位数",         3, 1, 3),
        new CalcType("div1doral",  "整十整百数除以一位数口算", 3, 2, 4),
        new CalcType("div1d",      "除数是一位数的除法",      3, 2, 3),
        new CalcType("mul2dtens",  "两位数乘整十数口算",      3, 2, 4),
        new CalcType("mul2d2d",    "两位数乘两位数",         3, 2, 3),
        new CalcType("fracsame",   "同分母分数加减",         3, 1, 3),
        new CalcType("dec1",       "一位小数加减",           3, 2, 4),
        // ── 四年级 ──
        new CalcType("mul3d2d",    "三位数乘两位数",         4, 1, 3),
        new CalcType("div2d",      "除数是两位数的除法",      4, 1, 3),
        new CalcType("mixops",     "四则混合运算",           4, 2, 2),
        new CalcType("simplify",   "运算定律与简便计算",      4, 2, 3),
        new CalcType("decaddsub",  "小数加减法",             4, 2, 4),
        // ── 五年级 ──
        new CalcType("decmul",     "小数乘法",               5, 1, 3),
        new CalcType("decdiv",     "小数除法",               5, 1, 3),
        new CalcType("equation",   "解简易方程",             5, 1, 3),
        new CalcType("fracdiff",   "异分母分数加减",         5, 2, 3),
        new CalcType("gcdlcm",     "最大公因数与最小公倍数",  5, 2, 2),
        new CalcType("reduce",     "约分",                   5, 2, 4),
        // ── 六年级 ──
        new CalcType("fracmul",    "分数乘法",               6, 1, 3),
        new CalcType("fracdiv",    "分数除法",               6, 1, 3),
        new CalcType("fracmix",    "分数乘除混合",           6, 1, 2),
        new CalcType("percent",    "百分数小数分数互化",      6, 1, 3),
        new CalcType("ratio",      "化简比与求比值",         6, 1, 2),
        new CalcType("proportion", "解比例",                 6, 2, 3)
    );

    private static final Map<String, CalcType> TYPE_MAP = new LinkedHashMap<>();
    static {
        for (CalcType t : TYPES) TYPE_MAP.put(t.code(), t);
    }

    /** 类型全表（按年级分组返回，供前端/agent 选型）。 */
    public List<Map<String, Object>> listTypes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CalcType t : TYPES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.code());
            m.put("name", t.name());
            m.put("grade", t.grade());
            m.put("term", t.term());
            out.add(m);
        }
        return out;
    }

    /**
     * 出卷：{title?, seed?, withGroupLabel?, groups:[{type, count, label?}]} → 双卷 PDF。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> export(Map<String, Object> body) {
        Object rawGroups = body.get("groups");
        if (!(rawGroups instanceof List<?> gl) || gl.isEmpty()) {
            throw new ServiceException("groups 不能为空：[{type, count}]", 400);
        }
        String title = str(body.get("title"), "口算训练");
        boolean withGroupLabel = body.get("withGroupLabel") == null || truthy(body.get("withGroupLabel"));
        long seed = body.get("seed") != null ? Long.parseLong(String.valueOf(body.get("seed")))
            : System.nanoTime();
        Random rnd = new Random(seed);

        int total = 0;
        List<Map<String, Object>> groupsOut = new ArrayList<>();
        for (Object o : gl) {
            Map<String, Object> g = (Map<String, Object>) o;
            String typeCode = str(g.get("type"), "");
            CalcType type = TYPE_MAP.get(typeCode);
            if (type == null) {
                throw new ServiceException("未知类型 " + typeCode + "，合法值见 GET /teacher/oralcalc/types", 400);
            }
            int count = g.get("count") == null ? 12 : Integer.parseInt(String.valueOf(g.get("count")));
            if (count < 1) continue;
            total += count;
            if (total > MAX_TOTAL) {
                throw new ServiceException("单卷总题数超上限 " + MAX_TOTAL, 400);
            }
            List<String[]> items = generate(type.code(), count, rnd);
            Map<String, Object> go = new LinkedHashMap<>();
            go.put("label", withGroupLabel ? str(g.get("label"), type.name()) : "");
            go.put("cols", type.cols());
            List<Map<String, String>> its = new ArrayList<>();
            for (String[] qa : items) {
                Map<String, String> it = new LinkedHashMap<>();
                it.put("q", qa[0]);
                it.put("a", qa[1]);
                its.add(it);
            }
            go.put("items", its);
            groupsOut.add(go);
        }

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("seed", String.valueOf(seed));
        for (String paper : List.of("question", "answer")) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("title", title);
            data.put("date", date);
            data.put("paper", paper);
            data.put("groups", groupsOut);
            byte[] pdf = renderer.render(THEME, data, paper);
            OssClient oss = OssFactory.instance();
            UploadResult up = oss.uploadSuffix(pdf, ".pdf", "application/pdf");
            result.put("question".equals(paper) ? "questionUrl" : "answerUrl", up.getUrl());
            log.info("[oralcalc] {} 卷生成 total={} size={}B url={}", paper, total, pdf.length, up.getUrl());
        }
        return result;
    }

    // ───────────────── 生成器（每类型一段，全部确定性约束） ─────────────────

    private List<String[]> generate(String code, int count, Random rnd) {
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int tries = 0;
        while (out.size() < count && tries++ < count * MAX_TRY) {
            String[] qa = genOne(code, rnd);
            if (qa == null || !seen.add(qa[0])) continue;
            out.add(qa);
        }
        if (out.size() < count) {
            log.warn("[oralcalc] 类型 {} 题量不足：要 {} 只生成 {}（数域太小去重耗尽）", code, count, out.size());
        }
        return out;
    }

    private String[] genOne(String code, Random rnd) {
        return switch (code) {
            // ── 一年级 ──
            case "add5" -> {
                int a = ri(rnd, 1, 4), b = ri(rnd, 1, 5 - a);
                yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
            }
            case "sub5" -> {
                int a = ri(rnd, 1, 5), b = ri(rnd, 1, a);
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "addsub10" -> {
                if (rnd.nextBoolean()) {
                    int a = ri(rnd, 1, 9), b = ri(rnd, 1, 10 - a);
                    yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                int a = ri(rnd, 2, 10), b = ri(rnd, 1, a - 1);
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "mix10" -> {
                int a = ri(rnd, 1, 9);
                boolean p1 = rnd.nextBoolean();
                int b = p1 ? ri(rnd, 1, 10 - a) : ri(rnd, 1, a);
                int m = p1 ? a + b : a - b;
                boolean p2 = m >= 10 ? false : (m <= 0 ? true : rnd.nextBoolean());
                int c = p2 ? ri(rnd, 1, 10 - m) : ri(rnd, 1, Math.max(1, m));
                if (!p2 && c > m) yield null;
                int r = p2 ? m + c : m - c;
                yield qa(a + (p1 ? "＋" : "－") + b + (p2 ? "＋" : "－") + c + "＝", String.valueOf(r));
            }
            case "addsub20nc" -> {
                if (rnd.nextBoolean()) {
                    int a = ri(rnd, 10, 18), b = ri(rnd, 1, 19 - a + (a % 10 == 0 ? 0 : 9 - a % 10) - Math.max(0, 19 - a - (9 - a % 10)));
                    b = ri(rnd, 1, 9 - a % 10);
                    if (b < 1) yield null;
                    yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                int a = ri(rnd, 11, 19), b = ri(rnd, 1, a % 10);
                if (b < 1) yield null;
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "add20c" -> {
                int a = ri(rnd, 2, 9), b = ri(rnd, 11 - a, 9);
                yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
            }
            case "sub20b" -> {
                int a = ri(rnd, 11, 18), b = ri(rnd, a % 10 + 1, 9);
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "addsub20" -> genOne(rnd.nextBoolean() ? "add20c" : "sub20b", rnd);
            case "tens" -> {
                if (rnd.nextBoolean()) {
                    int a = ri(rnd, 1, 8) * 10, b = ri(rnd, 1, (100 - a) / 10) * 10;
                    yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                int a = ri(rnd, 2, 9) * 10, b = ri(rnd, 1, a / 10 - 1) * 10;
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "t2d1d" -> {
                int a = ri(rnd, 21, 98);
                if (rnd.nextBoolean()) {
                    if (a % 10 == 9) yield null;
                    int b = ri(rnd, 1, 9 - a % 10);
                    yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                if (a % 10 == 0) yield null;
                int b = ri(rnd, 1, a % 10);
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "t2d1dc" -> {
                int a = ri(rnd, 21, 89);
                if (rnd.nextBoolean()) {
                    if (a % 10 == 0) yield null;
                    int b = ri(rnd, 10 - a % 10 + 1 > 9 ? 9 : 11 - a % 10, 9);
                    if (a % 10 + b < 10) yield null;
                    yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                if (a % 10 == 9) yield null;
                int b = ri(rnd, a % 10 + 1, 9);
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "t2dtens" -> {
                int a = ri(rnd, 11, 89);
                if (rnd.nextBoolean()) {
                    int max = (99 - a) / 10;
                    if (max < 1) yield null;
                    int b = ri(rnd, 1, max) * 10;
                    yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                int max = a / 10 - 1;
                if (max < 1) yield null;
                int b = ri(rnd, 1, max) * 10;
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            // ── 二年级 ──
            case "add2d2d" -> {
                int a = ri(rnd, 11, 88), b = ri(rnd, 11, 99 - a);
                if (b < 11) yield null;
                yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
            }
            case "sub2d2d" -> {
                int a = ri(rnd, 22, 99), b = ri(rnd, 11, a - 1);
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "mix100" -> {
                int a = ri(rnd, 20, 80);
                boolean p1 = rnd.nextBoolean();
                int b = p1 ? ri(rnd, 10, 99 - a) : ri(rnd, 10, a - 5);
                if (b < 10) yield null;
                int m = p1 ? a + b : a - b;
                boolean p2 = rnd.nextBoolean();
                int c = p2 ? ri(rnd, 10, Math.max(10, 99 - m)) : ri(rnd, 10, Math.max(10, m));
                if (p2 && m + c > 99) yield null;
                if (!p2 && c > m) yield null;
                int r = p2 ? m + c : m - c;
                yield qa(a + (p1 ? "＋" : "－") + b + (p2 ? "＋" : "－") + c + "＝", String.valueOf(r));
            }
            case "multable" -> {
                int a = ri(rnd, 2, 9), b = ri(rnd, 2, 9);
                yield qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "divtable" -> {
                int b = ri(rnd, 2, 9), q = ri(rnd, 2, 9);
                yield qa((b * q) + "÷" + b + "＝", String.valueOf(q));
            }
            case "muladd" -> {
                int a = ri(rnd, 2, 9), b = ri(rnd, 2, 9);
                boolean plus = rnd.nextBoolean();
                int c = plus ? ri(rnd, 2, 100 - a * b) : ri(rnd, 2, a * b - 1);
                if (plus && a * b + c > 100) yield null;
                yield qa(a + "×" + b + (plus ? "＋" : "－") + c + "＝", String.valueOf(plus ? a * b + c : a * b - c));
            }
            case "divrem" -> {
                int b = ri(rnd, 2, 9), q = ri(rnd, 2, 9), r = ri(rnd, 1, b - 1);
                yield qa((b * q + r) + "÷" + b + "＝", q + "……" + r);
            }
            case "paren" -> {
                if (rnd.nextBoolean()) {
                    int b = ri(rnd, 5, 40), c = ri(rnd, 5, 40), a = ri(rnd, b + c + 5, 99);
                    if (a > 99) yield null;
                    yield qa(a + "－(" + b + "＋" + c + ")＝", String.valueOf(a - b - c));
                }
                int c = ri(rnd, 2, 9), q = ri(rnd, 2, 9);
                int s = c * q, b2 = ri(rnd, 1, s - 1);
                yield qa("(" + (s - b2) + "＋" + b2 + ")÷" + c + "＝", String.valueOf(q));
            }
            // ── 三年级 ──
            case "add3d" -> {
                if (rnd.nextBoolean()) {
                    int a = ri(rnd, 120, 780), b = ri(rnd, 110, 999 - a);
                    if (b < 110) yield null;
                    yield qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                int a = ri(rnd, 250, 999), b = ri(rnd, 110, a - 50);
                yield qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            case "mul1doral" -> {
                int a = ri(rnd, 2, 9) * (rnd.nextBoolean() ? 10 : 100), b = ri(rnd, 2, 9);
                yield qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "mul1d" -> {
                int a = ri(rnd, 2, 5) > 3 ? ri(rnd, 112, 987) : ri(rnd, 12, 98);
                int b = ri(rnd, 2, 9);
                yield qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "div1doral" -> {
                int b = ri(rnd, 2, 9), q = ri(rnd, 2, 9) * (rnd.nextBoolean() ? 10 : 100);
                yield qa((b * q) + "÷" + b + "＝", String.valueOf(q));
            }
            case "div1d" -> {
                int b = ri(rnd, 2, 9), q = ri(rnd, 13, 160);
                yield qa((b * q) + "÷" + b + "＝", String.valueOf(q));
            }
            case "mul2dtens" -> {
                int a = ri(rnd, 12, 99), b = ri(rnd, 1, 9) * 10;
                yield qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "mul2d2d" -> {
                int a = ri(rnd, 12, 98), b = ri(rnd, 12, 98);
                yield qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "fracsame" -> {
                int m = ri(rnd, 3, 10);
                if (rnd.nextBoolean()) {
                    int a = ri(rnd, 1, m - 2), b = ri(rnd, 1, m - 1 - a);
                    yield qa(frac(a, m) + "＋" + frac(b, m) + "＝", fracAns(a + b, m));
                }
                int a = ri(rnd, 2, m - 1), b = ri(rnd, 1, a - 1);
                yield qa(frac(a, m) + "－" + frac(b, m) + "＝", fracAns(a - b, m));
            }
            case "dec1" -> {
                int a = ri(rnd, 5, 99), b;
                if (rnd.nextBoolean()) {
                    b = ri(rnd, 3, 99 - a > 3 ? 99 - a : 3);
                    if (a + b > 99) yield null;
                    yield qa(dec1(a) + "＋" + dec1(b) + "＝", dec1(a + b));
                }
                b = ri(rnd, 1, a - 1);
                yield qa(dec1(a) + "－" + dec1(b) + "＝", dec1(a - b));
            }
            // ── 四年级 ──
            case "mul3d2d" -> {
                int a = ri(rnd, 112, 987), b = ri(rnd, 12, 98);
                yield qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "div2d" -> {
                int b = ri(rnd, 12, 89);
                if (rnd.nextBoolean()) {
                    int q = ri(rnd, 3, 40);
                    yield qa((b * q) + "÷" + b + "＝", String.valueOf(q));
                }
                int q = ri(rnd, 3, 30), r = ri(rnd, 1, b - 1);
                yield qa((b * q + r) + "÷" + b + "＝", q + "……" + r);
            }
            case "mixops" -> {
                int form = rnd.nextInt(3);
                if (form == 0) {
                    int a = ri(rnd, 30, 300), b = ri(rnd, 20, 200), c = ri(rnd, 2, 9);
                    yield qa("(" + a + "＋" + b + ")×" + c + "＝", String.valueOf((a + b) * c));
                }
                if (form == 1) {
                    int c = ri(rnd, 2, 9), q = ri(rnd, 20, 90);
                    int a = ri(rnd, c * q + 50, 999);
                    if (a > 999) yield null;
                    yield qa(a + "－" + (c * q) + "÷" + c + "＝", String.valueOf(a - q));
                }
                int a = ri(rnd, 12, 40), b = ri(rnd, 40, 99), c = ri(rnd, 11, b - 5);
                yield qa(a + "×(" + b + "－" + c + ")＝", String.valueOf(a * (b - c)));
            }
            case "simplify" -> {
                int form = rnd.nextInt(5);
                if (form == 0) {
                    int n = ri(rnd, 3, 30);
                    yield qa("25×" + n + "×4＝", String.valueOf(100 * n));
                }
                if (form == 1) {
                    int n = ri(rnd, 3, 30);
                    yield qa("125×" + n + "×8＝", String.valueOf(1000 * n));
                }
                if (form == 2) {
                    int a = ri(rnd, 11, 89), c = 100 - a, b = ri(rnd, 15, 99);
                    yield qa(a + "＋" + b + "＋" + c + "＝", String.valueOf(100 + b));
                }
                if (form == 3) {
                    int b = ri(rnd, 21, 49), c = ri(rnd, 11, 40), a = ri(rnd, b + c + 20, 300);
                    int u = ri(rnd, 1, 9);
                    int bb = b * 10 + u, cc = c * 10 + (10 - u);
                    if (a < (bb + cc) / 10 + 30) yield null;
                    yield qa(a + "－" + (bb / 10) + "－" + (cc / 10) + "＝", String.valueOf(a - bb / 10 - cc / 10));
                }
                int n = ri(rnd, 2, 9), m = ri(rnd, 2, 99);
                yield qa("99×" + m + "＋" + m + "＝", String.valueOf(100 * m));
            }
            case "decaddsub" -> {
                int a = ri(rnd, 100, 9900), b;
                if (rnd.nextBoolean()) {
                    b = ri(rnd, 100, 9999 - a > 100 ? 9999 - a : 100);
                    if (a + b > 9999) yield null;
                    yield qa(dec2(a) + "＋" + dec2(b) + "＝", dec2(a + b));
                }
                b = ri(rnd, 99, a - 1);
                yield qa(dec2(a) + "－" + dec2(b) + "＝", dec2(a - b));
            }
            // ── 五年级 ──
            case "decmul" -> {
                if (rnd.nextBoolean()) {
                    int a = ri(rnd, 11, 99), b = ri(rnd, 2, 9);
                    yield qa(dec1(a) + "×" + b + "＝", dec1(a * b));
                }
                int a = ri(rnd, 2, 99), b = ri(rnd, 2, 9);
                yield qa(dec1(a) + "×" + dec1(b) + "＝", dec2(a * b));
            }
            case "decdiv" -> {
                if (rnd.nextBoolean()) {
                    int b = ri(rnd, 2, 9), q = ri(rnd, 3, 99);
                    yield qa(dec1(b * q) + "÷" + b + "＝", dec1(q));
                }
                int b = ri(rnd, 2, 9), q = ri(rnd, 2, 90);
                yield qa(dec1(q * b) + "÷" + dec1(b) + "＝", String.valueOf(q));
            }
            case "equation" -> {
                int form = rnd.nextInt(4);
                int x = ri(rnd, 3, 60);
                if (form == 0) {
                    int a = ri(rnd, 5, 80);
                    yield qa("x＋" + a + "＝" + (x + a), "x＝" + x);
                }
                if (form == 1) {
                    int a = ri(rnd, 2, x - 1 > 2 ? x - 1 : 2);
                    if (x - a < 1) yield null;
                    yield qa("x－" + a + "＝" + (x - a), "x＝" + x);
                }
                if (form == 2) {
                    int a = ri(rnd, 2, 9);
                    yield qa(a + "x＝" + (a * x), "x＝" + x);
                }
                int a = ri(rnd, 2, 9), b = ri(rnd, 2, 50);
                yield qa(a + "x＋" + b + "＝" + (a * x + b), "x＝" + x);
            }
            case "fracdiff" -> {
                int m = ri(rnd, 2, 9), n = ri(rnd, 2, 12);
                if (m == n || n % m == 0 || m % n == 0 && rnd.nextBoolean()) yield null;
                int a = ri(rnd, 1, m - 1), b = ri(rnd, 1, n - 1);
                if (rnd.nextBoolean()) {
                    yield qa(frac(a, m) + "＋" + frac(b, n) + "＝", fracAns(a * n + b * m, m * n));
                }
                if (a * n - b * m <= 0) yield null;
                yield qa(frac(a, m) + "－" + frac(b, n) + "＝", fracAns(a * n - b * m, m * n));
            }
            case "gcdlcm" -> {
                int g = ri(rnd, 2, 6), p = ri(rnd, 2, 8), q = ri(rnd, 2, 8);
                if (p == q || gcd(p, q) != 1) yield null;
                int a = g * p, b = g * q;
                if (a > 60 || b > 60) yield null;
                if (rnd.nextBoolean()) {
                    yield qa("（" + a + "，" + b + "）的最大公因数＝", String.valueOf(g));
                }
                yield qa("［" + a + "，" + b + "］的最小公倍数＝", String.valueOf(g * p * q));
            }
            case "reduce" -> {
                int k = ri(rnd, 2, 9), a = ri(rnd, 1, 9), b = ri(rnd, a + 1, 12);
                if (gcd(a, b) != 1) yield null;
                yield qa(frac(a * k, b * k) + "＝", frac(a, b));
            }
            // ── 六年级 ──
            case "fracmul" -> {
                if (rnd.nextBoolean()) {
                    int b = ri(rnd, 2, 12), a = ri(rnd, 1, b - 1), n = ri(rnd, 2, 12);
                    yield qa(frac(a, b) + "×" + n + "＝", fracAns(a * n, b));
                }
                int b = ri(rnd, 2, 9), a = ri(rnd, 1, b - 1);
                int d = ri(rnd, 2, 9), c = ri(rnd, 1, d - 1);
                yield qa(frac(a, b) + "×" + frac(c, d) + "＝", fracAns(a * c, b * d));
            }
            case "fracdiv" -> {
                if (rnd.nextBoolean()) {
                    int b = ri(rnd, 2, 9), a = ri(rnd, 1, b - 1), n = ri(rnd, 2, 9);
                    yield qa(frac(a, b) + "÷" + n + "＝", fracAns(a, b * n));
                }
                int b = ri(rnd, 2, 9), a = ri(rnd, 1, b - 1);
                int d = ri(rnd, 2, 9), c = ri(rnd, 1, d - 1);
                yield qa(frac(a, b) + "÷" + frac(c, d) + "＝", fracAns(a * d, b * c));
            }
            case "fracmix" -> {
                int b = ri(rnd, 2, 6), a = ri(rnd, 1, b - 1);
                int n = ri(rnd, 2, 8);
                int d = ri(rnd, 2, 6), c = ri(rnd, 1, d - 1);
                yield qa(frac(a, b) + "×" + n + "÷" + frac(c, d) + "＝", fracAns(a * n * d, b * c));
            }
            case "percent" -> {
                int form = rnd.nextInt(3);
                if (form == 0) {
                    int n = ri(rnd, 1, 99);
                    yield qa(dec2(n) + "＝（　　）%", n + "%");
                }
                if (form == 1) {
                    int b = List.of(2, 4, 5, 10, 20, 25, 50).get(rnd.nextInt(7));
                    int a = ri(rnd, 1, b - 1);
                    if (gcd(a, b) != 1) yield null;
                    yield qa(frac(a, b) + "＝（　　）%", (a * 100 / b) + "%");
                }
                int n = ri(rnd, 1, 99);
                yield qa(n + "%＝（　　）（小数）", dec2(n));
            }
            case "ratio" -> {
                int g = ri(rnd, 2, 9), p = ri(rnd, 2, 9), q = ri(rnd, 2, 9);
                if (p == q || gcd(p, q) != 1) yield null;
                if (rnd.nextBoolean()) {
                    yield qa((g * p) + "∶" + (g * q) + " 化成最简整数比＝", p + "∶" + q);
                }
                yield qa((g * p) + "∶" + (g * q) + " 的比值＝", fracAns(p, q));
            }
            case "proportion" -> {
                int c = ri(rnd, 2, 9), x = ri(rnd, 2, 30);
                int b = ri(rnd, 2, 9);
                yield qa("x∶" + b + "＝" + (x * c) + "∶" + (b * c), "x＝" + x);
            }
            default -> throw new ServiceException("未知类型 " + code, 400);
        };
    }

    // ───────────────── 小工具 ─────────────────

    private String[] qa(String q, String a) {
        return new String[]{q, a};
    }

    private int ri(Random rnd, int lo, int hi) {
        if (hi < lo) return lo;
        return lo + rnd.nextInt(hi - lo + 1);
    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    /** 分数 KaTeX（不约分，用于题面）。 */
    private String frac(int a, int b) {
        return "$\\frac{" + a + "}{" + b + "}$";
    }

    /** 分数答案：约分 + 假分数化带分数/整数。 */
    private String fracAns(int a, int b) {
        int g = gcd(a, b);
        a /= g;
        b /= g;
        if (b == 1) return String.valueOf(a);
        if (a > b) {
            int k = a / b, r = a % b;
            return "$" + k + "\\frac{" + r + "}{" + b + "}$";
        }
        return frac(a, b);
    }

    /** n/10 一位小数（整数缩放避浮点）。 */
    private String dec1(int n) {
        return (n / 10) + "." + (n % 10);
    }

    /** n/100 两位小数（末尾 0 保留一位）。 */
    private String dec2(int n) {
        int i = n / 100, f = n % 100;
        if (f % 10 == 0) return i + "." + (f / 10);
        return i + "." + (f < 10 ? "0" + f : String.valueOf(f));
    }

    private boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        return o != null && ("true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o)));
    }

    private String str(Object o, String dft) {
        if (o == null) return dft;
        String s = String.valueOf(o);
        return s.isBlank() ? dft : s;
    }
}
