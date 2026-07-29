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
        new CalcType("addsub3d",   "三位数加减混合",         3, 1, 2),
        new CalcType("mul1doral",  "整十整百数乘一位数口算",  3, 1, 4),
        new CalcType("mul1d",      "多位数乘一位数",         3, 1, 3),
        new CalcType("div1doral",  "整十整百数除以一位数口算", 3, 2, 4),
        new CalcType("div1d",      "除数是一位数的除法",      3, 2, 3),
        new CalcType("mul2dtens",  "两位数乘整十数口算",      3, 2, 4),
        new CalcType("mul2d2d",    "两位数乘两位数",         3, 2, 3),
        new CalcType("mixops3",    "三步混合运算",           3, 2, 4),
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
            m.put("cols", t.cols());   // 每行题数：前端预告「凑行」后的实际题量用
            out.add(m);
        }
        return out;
    }

    /**
     * 只出题目数据、不渲染 PDF —— agent 拿 {q,a} 塞进自有版面（每日一练等）。
     *
     * <p>入参与 {@link #export} 完全一致，返回 {total, seed, groups:[{label,cols,mode,items:[{q,a}]}]}。
     */
    public Map<String, Object> generateItems(Map<String, Object> body) {
        Built b = build(body);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", b.total());
        r.put("seed", String.valueOf(b.seed()));
        r.put("groups", b.groups());
        return r;
    }

    /** build 产物：题目数据 + 元信息（export 与 generateItems 共用）。 */
    private record Built(int total, long seed, List<Map<String, Object>> groups) {}

    /**
     * 出卷：{title?, seed?, withGroupLabel?, groups:[{type, count, label?, level?}]} → 双卷 PDF。
     */
    public Map<String, Object> export(Map<String, Object> body) {
        Built built = build(body);
        int total = built.total();
        List<Map<String, Object>> groupsOut = built.groups();
        String title = str(body.get("title"), "口算训练");
        return renderPapers(body, title, total, built.seed(), groupsOut);
    }

    /** 组题：验参 → 逐组生成 → 跨组去重（不碰渲染，供两个入口共用）。 */
    @SuppressWarnings("unchecked")
    private Built build(Map<String, Object> body) {
        Object rawGroups = body.get("groups");
        if (!(rawGroups instanceof List<?> gl) || gl.isEmpty()) {
            throw new ServiceException("groups 不能为空：[{type, count}]", 400);
        }
        boolean withGroupLabel = body.get("withGroupLabel") == null || truthy(body.get("withGroupLabel"));
        // 凑行补足：题数向上凑整到栏数倍数（每行凑满）。缺省开（MCP/agent 路径沿用 2026-07-18 拍板），
        // 自选出题页默认显式传 false=按填的题数原样生成（2026-07-19 用户拍板：补足做成可选不做默认）。
        boolean fillRows = body.get("fillRows") == null || truthy(body.get("fillRows"));
        long seed = parseSeed(body.get("seed"));
        Random rnd = new Random(seed);

        // 全局栏数覆盖（layout.cols）：给了则整卷统一栏宽，各组网格上下对齐
        Integer globalCols = null;
        if (body.get("layout") instanceof Map<?, ?> lm0 && lm0.get("cols") != null) {
            globalCols = Integer.parseInt(String.valueOf(lm0.get("cols")));
        }

        int total = 0;
        List<Map<String, Object>> groupsOut = new ArrayList<>();
        Set<String> seenAll = new LinkedHashSet<>();   // 🔴 跨组去重：混合组不复用前面组已出的算式
        for (Object o : gl) {
            Map<String, Object> g = (Map<String, Object>) o;
            String typeCode = str(g.get("type"), "");
            CalcType type = TYPE_MAP.get(typeCode);
            if (type == null) {
                throw new ServiceException("未知类型 " + typeCode + "，合法值见 GET /teacher/oralcalc/types", 400);
            }
            int count = g.get("count") == null ? 12 : Integer.parseInt(String.valueOf(g.get("count")));
            if (count < 1) continue;
            int cols = globalCols != null ? globalCols : type.cols();
            // 凑行补足（可选）：每组题数向上凑整到栏数的整数倍，网格每行凑满不留残行
            if (fillRows) count = ((count + cols - 1) / cols) * cols;
            total += count;
            if (total > MAX_TOTAL) {
                throw new ServiceException("单卷总题数超上限 " + MAX_TOTAL, 400);
            }
            // 难度档：basic 基础 / advanced 提高；空 = 原有随机行为（零回归）
            String level = str(g.get("level"), "");
            List<String[]> items = generate(type.code(), count, rnd, seenAll, level);
            Map<String, Object> go = new LinkedHashMap<>();
            go.put("label", withGroupLabel ? str(g.get("label"), type.name()) : "");
            go.put("cols", cols);
            // 呈现形态：oral=口算一行式(缺省) / vertical=竖式留白 / tuoshi=脱式留白(卷面去＝)
            String mode = str(g.get("mode"), "oral");
            if (!"oral".equals(mode) && !"vertical".equals(mode) && !"tuoshi".equals(mode)) mode = "oral";
            go.put("mode", mode);
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
        return new Built(total, seed, groupsOut);
    }

    /** 渲染双卷 PDF → OSS（只被 export 用）。 */
    private Map<String, Object> renderPapers(Map<String, Object> body, String title, int total, long seed,
                                             List<Map<String, Object>> groupsOut) {
        // papers：['question'] 只出题目卷（口算主场景，老师不需要答案卷）；缺省双卷。
        List<String> papers = new ArrayList<>();
        if (body.get("papers") instanceof List<?> pl) {
            for (Object o : pl) {
                String s = String.valueOf(o);
                if ("question".equals(s) || "answer".equals(s)) papers.add(s);
            }
        }
        if (papers.isEmpty()) {
            papers.add("question");
            papers.add("answer");
        }

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("seed", String.valueOf(seed));
        for (String paper : papers) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("title", title);
            data.put("date", date);
            data.put("paper", paper);
            data.put("total", total);
            // layout 版式配置透传（cols/numbered/rowHeightMm/fontSizePt/footer/frame），主题侧有缺省
            if (body.get("layout") instanceof Map<?, ?> lm) data.put("layout", lm);
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

    /** seed 容错：数字直用；任意字符串取 hashCode（同串同卷可复现）；空 = 随机。非数字不再裸 500。 */
    private static long parseSeed(Object raw) {
        if (raw == null) return System.nanoTime();
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return System.nanoTime();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    private List<String[]> generate(String code, int count, Random rnd, Set<String> seen, String level) {
        List<String[]> out = new ArrayList<>();
        int tries = 0;
        while (out.size() < count && tries++ < count * MAX_TRY) {
            String[] qa = genOne(code, rnd, level);
            if (qa == null || !seen.add(qa[0])) continue;
            out.add(qa);
        }
        if (out.size() < count) {
            // 数域太小去重耗尽（如 5 以内加法全集有限）：放开去重补足——卷面每行必须凑满，
            // 少量重复算式比残行可接受（口算本就反复练）。
            log.warn("[oralcalc] 类型 {} 去重耗尽（{}/{}），放开去重补足", code, out.size(), count);
            int guard = 0;
            while (out.size() < count && guard++ < count * MAX_TRY) {
                String[] qa = genOne(code, rnd, level);
                if (qa != null) out.add(qa);
            }
        }
        return out;
    }

    /** 支持难度档的类型（三年级计算谱系先落地）；其余类型忽略 level、走原随机逻辑。 */
    private static final Set<String> LEVELED =
        Set.of("mul2d2d", "mul1d", "div1d", "dec1", "add3d", "mul2dtens", "mixops3",
               "mul1doral", "div1doral");

    private String[] genOne(String code, Random rnd, String level) {
        // 🔴 支持档位的类型：genLeveled 返回 null = 本次摇的不满足档位约束，交给 generate 重摇，
        //    **绝不回落原随机逻辑** —— 回落会漏出不符合档位的题（如基础档出现首位不够除的除法）。
        if (!level.isEmpty() && LEVELED.contains(code)) {
            return genLeveled(code, rnd, level);
        }
        return genOne(code, rnd);
    }

    /**
     * 难度档生成器：{@code basic} 基础 / {@code advanced} 提高。
     *
     * <p>难度靠**客观开关**控制，不靠估计：进位次数 / 因数是否含 0 / 首位是否够除 / 是否退位。
     * 只覆盖三年级计算谱系（每日一练双版本的落点）；其余类型返回 null → 回落原随机逻辑。
     * 生成不满足档位约束时返回 null，由 {@link #generate} 的重试循环重摇。
     */
    private String[] genLeveled(String code, Random rnd, String level) {
        boolean adv = "advanced".equals(level);
        switch (code) {
            case "mul1doral" -> {
                // 🔴 basic=原随机（整十整百乘一位）；提高堵"20×2/70×3 送分"（2026-07-30 目检）：
                //   两位数乘一位数口算（非整十）+ 必须进位（个位积≥10）——三上口算真实提高档。
                if (!adv) return genOne(code, rnd);
                int a = ri(rnd, 12, 99), b = ri(rnd, 3, 9);
                if (a % 10 == 0 || (a % 10) * b < 10 || a * b > 999) return null;
                return qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "div1doral" -> {
                // basic=原随机（整十整百商）；提高：两位数÷一位数整除口算（商非整十，如 78÷6＝13）。
                if (!adv) return genOne(code, rnd);
                int b = ri(rnd, 3, 9), q = ri(rnd, 12, 19);
                if (q % 10 == 0 || b * q > 99) return null;
                return qa((b * q) + "÷" + b + "＝", String.valueOf(q));
            }
            case "mul2d2d" -> {
                // 基础=各位数字 1-3（各位积 <10，竖式不进位）
                // 提高（2026-07-30 放宽）：各位 4-9 数域太窄，一卷里反复出 75×76/77×75 雷同；
                //   改为各位 3-9 + 个位积进位（(a%10)*(b%10)≥10）+ a≠b —— 数域变大，进位保证不丢。
                if (!adv) {
                    int a0 = ri(rnd, 1, 3) * 10 + ri(rnd, 1, 3);
                    int b0 = ri(rnd, 1, 3) * 10 + ri(rnd, 1, 3);
                    return qa(a0 + "×" + b0 + "＝", String.valueOf(a0 * b0));
                }
                int a = ri(rnd, 3, 9) * 10 + ri(rnd, 3, 9);
                int b = ri(rnd, 3, 9) * 10 + ri(rnd, 3, 9);
                if (a == b || (a % 10) * (b % 10) < 10) return null;
                return qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "mul2dtens" -> {
                // 🔴 basic = 原随机逻辑（零回归，G 回归线）；提高堵"61×10 送分"：
                //   乘数排除 10（20-90 整十），被乘数个位非 0（必须真进位算两步）。
                if (!adv) return genOne(code, rnd);
                int a = ri(rnd, 12, 99), b = ri(rnd, 2, 9) * 10;
                if (a % 10 == 0) return null;
                return qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "mixops3" -> {
                // 基础=两步（数域 100 内）；提高=三步跨级（含括号/两级运算）
                return genMixops3(rnd, adv);
            }
            case "mul1d" -> {
                if (!adv) {
                    int a = ri(rnd, 1, 3) * 100 + ri(rnd, 0, 3) * 10 + ri(rnd, 1, 3);
                    int b = ri(rnd, 2, 3);
                    return qa(a + "×" + b + "＝", String.valueOf(a * b));
                }
                if (rnd.nextBoolean()) {                       // 因数中间有 0
                    int a = ri(rnd, 1, 9) * 100 + ri(rnd, 1, 9), b = ri(rnd, 4, 9);
                    return qa(a + "×" + b + "＝", String.valueOf(a * b));
                }
                int a = ri(rnd, 6, 9) * 100 + ri(rnd, 6, 9) * 10 + ri(rnd, 6, 9);
                int b = ri(rnd, 6, 9);                         // 连续进位
                return qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "div1d" -> {
                int b = ri(rnd, 2, 9);
                if (!adv) {
                    // 基础 = 两位数÷一位数、整除、首位够除（商两位数）——笔算除法起步形态。
                    // 🔴 按位数构造而非碰运气：q 上界锁 99/b，保证被除数是两位数，首位必 ≥ b。
                    int maxQ = 99 / b;
                    if (maxQ < 11) return null;
                    int q = ri(rnd, 11, maxQ);
                    int d = b * q;
                    return firstDigit(d) < b ? null : qa(d + "÷" + b + "＝", String.valueOf(q));
                }
                int q = ri(rnd, 11, 99);
                if (rnd.nextBoolean()) {                       // 首位不够除（商比被除数少一位）
                    int d = b * q;
                    return firstDigit(d) >= b ? null : qa(d + "÷" + b + "＝", String.valueOf(q));
                }
                int r = ri(rnd, 1, b - 1);                     // 有余数
                return qa((b * q + r) + "÷" + b + "＝", q + "……" + r);
            }
            case "dec1" -> {
                // 以「十分之一」为整数单位算，规避浮点误差；dec1() 负责回显小数
                if (!adv) {
                    if (rnd.nextBoolean()) {                    // 不进位加
                        int a = ri(rnd, 11, 80), b = ri(rnd, 11, 80);
                        return (a % 10 + b % 10 >= 10 || a + b > 999) ? null
                            : qa(dec1(a) + "＋" + dec1(b) + "＝", dec1(a + b));
                    }
                    int a = ri(rnd, 21, 99), b = ri(rnd, 11, a - 10);   // 不退位减
                    return a % 10 < b % 10 ? null : qa(dec1(a) + "－" + dec1(b) + "＝", dec1(a - b));
                }
                int form = rnd.nextInt(3);
                if (form == 0) {                               // 进位加
                    int a = ri(rnd, 11, 90), b = ri(rnd, 11, 90);
                    return a % 10 + b % 10 < 10 ? null : qa(dec1(a) + "＋" + dec1(b) + "＝", dec1(a + b));
                }
                if (form == 1) {                               // 退位减
                    int a = ri(rnd, 21, 99), b = ri(rnd, 11, a - 5);
                    return a % 10 >= b % 10 ? null : qa(dec1(a) + "－" + dec1(b) + "＝", dec1(a - b));
                }
                int a = ri(rnd, 3, 9) * 10, b = ri(rnd, 11, a - 10);    // 整数减小数（9－4.7）
                return b % 10 == 0 ? null : qa((a / 10) + "－" + dec1(b) + "＝", dec1(a - b));
            }
            case "add3d" -> {
                if (!adv) {
                    if (rnd.nextBoolean()) {                    // 不进位加
                        int a = d3(rnd, 1, 4), b = d3(rnd, 1, 4);
                        return carryAdd(a, b) > 0 ? null : qa(a + "＋" + b + "＝", String.valueOf(a + b));
                    }
                    int a = d3(rnd, 5, 9), b = d3(rnd, 1, 4);  // 不退位减
                    return (a <= b || borrowSub(a, b) > 0) ? null
                        : qa(a + "－" + b + "＝", String.valueOf(a - b));
                }
                if (rnd.nextBoolean()) {                       // 连续进位加
                    int a = ri(rnd, 150, 780), b = ri(rnd, 150, 999 - a);
                    return (b < 150 || carryAdd(a, b) < 2) ? null
                        : qa(a + "＋" + b + "＝", String.valueOf(a + b));
                }
                int a = ri(rnd, 300, 999), b = ri(rnd, 110, a - 50);   // 连续退位减
                return borrowSub(a, b) < 2 ? null : qa(a + "－" + b + "＝", String.valueOf(a - b));
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * 三步混合运算（mixops3，三下两级运算落点）。
     *
     * <p>硬约束（全部程序保证，不靠碰运气）：<b>全整数</b>／<b>每步除法必整除</b>／
     * <b>中间结果恒正</b>／<b>终值 ≤ 1000</b>／<b>除数一位数</b>（{@code A÷(B×C)} 形态里 B×C ≤ 9）。
     * 构造式生成（先定商再乘回被除数），不满足边界时返回 null 交 {@link #generate} 重摇。
     *
     * @param three true=三步跨级形态（提高档/无档缺省）；false=两步形态（基础档，数域 100 内）
     */
    private String[] genMixops3(Random rnd, boolean three) {
        if (!three) {
            // ── 基础：两步，结果 ≤100 ──
            switch (rnd.nextInt(5)) {
                case 0 -> {                                     // A－B÷C
                    int c = ri(rnd, 2, 9), q = ri(rnd, 2, 9), b = c * q;
                    int a = ri(rnd, q + 1, 99);
                    return qa(a + "－" + b + "÷" + c + "＝", String.valueOf(a - q));
                }
                case 1 -> {                                     // (A＋B)÷C
                    int c = ri(rnd, 2, 9), s = c * ri(rnd, 2, 99 / c);
                    if (s < 10) return null;
                    int a = ri(rnd, 5, s - 5);
                    if (a < 5 || s - a < 5) return null;
                    return qa("(" + a + "＋" + (s - a) + ")÷" + c + "＝", String.valueOf(s / c));
                }
                case 2 -> {                                     // A×B－C
                    int a = ri(rnd, 2, 9), b = ri(rnd, 2, 9), p = a * b;
                    if (p < 6) return null;
                    int c = ri(rnd, 2, p - 1);
                    return qa(a + "×" + b + "－" + c + "＝", String.valueOf(p - c));
                }
                case 3 -> {                                     // A×B＋C
                    int a = ri(rnd, 2, 9), b = ri(rnd, 2, 9), p = a * b;
                    if (p > 90) return null;
                    int c = ri(rnd, 2, 100 - p);
                    return qa(a + "×" + b + "＋" + c + "＝", String.valueOf(p + c));
                }
                default -> {                                    // A÷B＋C
                    int b = ri(rnd, 2, 9), q = ri(rnd, 2, 9), c = ri(rnd, 2, 90);
                    if (q + c > 100) return null;
                    return qa((b * q) + "÷" + b + "＋" + c + "＝", String.valueOf(q + c));
                }
            }
        }
        // ── 提高：三步跨级（五种形态） ──
        switch (rnd.nextInt(5)) {
            case 0 -> {                                         // A－B÷C×D
                int c = ri(rnd, 2, 9), q = ri(rnd, 2, 9), b = c * q, d = ri(rnd, 2, 9);
                if (c == d) return null;                        // 🔴 ÷C×D 同数相消=退化一步（verifier S5）
                int p = q * d;
                if (p >= 990) return null;
                int a = ri(rnd, p + 1, 999);
                return qa(a + "－" + b + "÷" + c + "×" + d + "＝", String.valueOf(a - p));
            }
            case 1 -> {                                         // (A＋B)÷C×D
                int c = ri(rnd, 2, 9), q = ri(rnd, 2, 20), s = c * q, d = ri(rnd, 2, 9);
                if (c == d) return null;                        // 同上：÷C×D 相消防退化
                if (s < 12 || q * d > 1000) return null;
                int a = ri(rnd, 5, s - 5);
                if (a < 5 || s - a < 5) return null;
                return qa("(" + a + "＋" + (s - a) + ")÷" + c + "×" + d + "＝", String.valueOf(q * d));
            }
            case 2 -> {                                         // A×(B÷C－D)
                int c = ri(rnd, 2, 9), q = ri(rnd, 3, 12), b = c * q;
                int d = ri(rnd, 1, q - 1), a = ri(rnd, 2, 9);
                if (q - d < 1 || a * (q - d) > 1000) return null;
                return qa(a + "×(" + b + "÷" + c + "－" + d + ")＝", String.valueOf(a * (q - d)));
            }
            case 3 -> {                                         // A÷(B×C)＋D（🔴 括号内积 ≤9）
                int b = ri(rnd, 2, 4), c = ri(rnd, 2, 9 / b);
                int m = b * c;
                if (m < 4) return null;
                int q = ri(rnd, 3, 99), d = ri(rnd, 2, 99);
                if (q + d > 1000) return null;
                return qa((m * q) + "÷(" + b + "×" + c + ")＋" + d + "＝", String.valueOf(q + d));
            }
            default -> {                                        // A－(B＋C)÷D
                int d = ri(rnd, 2, 9), q = ri(rnd, 2, 20), s = d * q;
                if (s < 12) return null;
                int b = ri(rnd, 5, s - 5);
                if (b < 5 || s - b < 5) return null;
                int a = ri(rnd, q + 1, 999);
                return qa(a + "－(" + b + "＋" + (s - b) + ")÷" + d + "＝", String.valueOf(a - q));
            }
        }
    }

    /** 三位数：各位数字都取 [lo,hi]（百位至少 1）。 */
    private int d3(Random rnd, int lo, int hi) {
        return ri(rnd, Math.max(lo, 1), hi) * 100 + ri(rnd, lo, hi) * 10 + ri(rnd, lo, hi);
    }

    private static int firstDigit(int n) {
        while (n >= 10) n /= 10;
        return n;
    }

    /** 竖式加法的进位次数。 */
    private static int carryAdd(int a, int b) {
        int cnt = 0, carry = 0;
        while (a > 0 || b > 0) {
            carry = (a % 10 + b % 10 + carry) >= 10 ? 1 : 0;
            cnt += carry;
            a /= 10;
            b /= 10;
        }
        return cnt;
    }

    /** 竖式减法的退位次数（a≥b）。 */
    private static int borrowSub(int a, int b) {
        int cnt = 0, borrow = 0;
        while (b > 0 || borrow > 0) {
            borrow = (a % 10 - b % 10 - borrow) < 0 ? 1 : 0;
            cnt += borrow;
            a /= 10;
            b /= 10;
        }
        return cnt;
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
            case "addsub3d" -> {
                int form = rnd.nextInt(3);
                if (form == 0) {
                    int a = ri(rnd, 120, 700), b = ri(rnd, 110, 999 - a), c = ri(rnd, 110, a + b - 60);
                    yield qa(a + "＋" + b + "－" + c + "＝", String.valueOf(a + b - c));
                }
                if (form == 1) {
                    int a = ri(rnd, 300, 999), b = ri(rnd, 110, a - 100), c = ri(rnd, 110, 999 - (a - b));
                    yield qa(a + "－" + b + "＋" + c + "＝", String.valueOf(a - b + c));
                }
                int a = ri(rnd, 400, 999), b = ri(rnd, 110, a - 220), c = ri(rnd, 100, a - b - 60);
                yield qa(a + "－" + b + "－" + c + "＝", String.valueOf(a - b - c));
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
            // 无档缺省 = 三步跨级形态（类型名即三步；两步形态走 level=basic）
            case "mixops3" -> genMixops3(rnd, true);
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
