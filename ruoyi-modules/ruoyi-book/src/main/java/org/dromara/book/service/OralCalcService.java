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
            List<Item> items = generate(type.code(), count, rnd, seenAll, level);
            Map<String, Object> go = new LinkedHashMap<>();
            go.put("label", withGroupLabel ? str(g.get("label"), type.name()) : "");
            go.put("cols", cols);
            // 呈现形态：oral=口算一行式(缺省) / vertical=竖式留白 / tuoshi=脱式留白(卷面去＝)
            String mode = str(g.get("mode"), "oral");
            if (!"oral".equals(mode) && !"vertical".equals(mode) && !"tuoshi".equals(mode)) mode = "oral";
            go.put("mode", mode);
            List<Map<String, Object>> its = new ArrayList<>();
            for (Item item : items) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("q", item.q());
                it.put("a", item.a());
                // 🔴 脱式分步（PRD-013 B5 清偿）：steps=逐步化简串，末项=终值；无分步的类型不出该字段，
                //    {q,a} 老消费方零影响（主题层有 steps 逐行印"＝xx"，无则回落只印结果）。
                if (!item.steps().isEmpty()) it.put("steps", item.steps());
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

    private List<Item> generate(String code, int count, Random rnd, Set<String> seen, String level) {
        GenCtx ctx = new GenCtx(rnd, level, count);
        List<Item> out = new ArrayList<>();
        int tries = 0;
        while (out.size() < count && tries++ < count * MAX_TRY) {
            Item it = genOne(code, ctx);
            // 三道闸：档位/琐碎闸（genOne 返 null 重摇）→ 同质配额闸（ctx.accept）→ 跨组去重
            if (it == null || !ctx.accept(it) || !seen.add(it.q())) continue;
            ctx.mark(it);
            out.add(it);
        }
        if (out.size() < count) {
            // 数域太小去重/配额耗尽（如 5 以内加法全集有限）：放开闸门补足——卷面每行必须凑满，
            // 少量重复算式比残行可接受（口算本就反复练）。
            log.warn("[oralcalc] 类型 {} 去重/配额耗尽（{}/{}），放开闸门补足", code, out.size(), count);
            ctx.relaxed = true;
            int guard = 0;
            while (out.size() < count && guard++ < count * MAX_TRY) {
                Item it = genOne(code, ctx);
                if (it != null) {
                    ctx.mark(it);
                    out.add(it);
                }
            }
        }
        return out;
    }

    /**
     * 出题产物：题面 {@code q} / 答案 {@code a} / 分步化简串 {@code steps} / 结构签名 {@code struct}。
     *
     * <p>{@code steps}（脱式解析用，其余类型空）：每项 = 完成一步运算后的**整条算式**（不带"＝"前缀），
     * 末项 = 终值（等于 {@code a}）。例 {@code 36－15×2＝} → {@code ["36－30","6"]}；
     * {@code (26＋52)÷6×5＝} → {@code ["78÷6×5","13×5","65"]}。
     *
     * <p>{@code struct}：同质闸 / 骨架轮换用的结构签名（如 {@code dec1:intdec}、{@code mix3:axb+cxd}），
     * **不出参**，只在一次 generate（= 一个 group）内做配额统计。null = 不参与同质闸。
     */
    private record Item(String q, String a, List<String> steps, String struct) {}

    /** 结构硬配额（不随题数放宽）：整数减小数一组最多 2 道（2026-07-30 目检：一行四道全是 6－2.7 型）。 */
    private static final Map<String, Integer> HARD_CAP = Map.of("dec1:intdec", 2);

    /**
     * 一次 generate（= 一个 group）的生成上下文：难度档 + 结构配额 + 骨架轮换。
     *
     * <p>🔴 同质闸（2026-07-30 审计）：同 group 内同结构占比 ≤60%（{@code cap}=round(count×0.6)，下限 1），
     * 另加 {@link #HARD_CAP} 硬配额；{@link #pickForm} 只在"未用满且用得最少"的结构里挑 —— 因此
     * 4 道 mixops3 必出 4 种骨架、4 道 mul2dtens 不会全是"两位数×整十"、一组小数不会全是"整数减小数"。
     *
     * <p>{@code relaxed}=true 是关卷兜底（去重/配额耗尽时放开闸门），保证卷面每行凑满不留残行。
     */
    private static final class GenCtx {
        final Random rnd;
        final String level;
        final int cap;
        final Map<String, Integer> used = new LinkedHashMap<>();
        boolean relaxed;

        GenCtx(Random rnd, String level, int count) {
            this.rnd = rnd;
            this.level = level == null ? "" : level;
            this.cap = Math.max(1, (int) Math.round(count * 0.6));
        }

        boolean adv() {
            return "advanced".equals(level);
        }

        boolean basic() {
            return "basic".equals(level);
        }

        private int limit(String struct) {
            return Math.min(cap, HARD_CAP.getOrDefault(struct, Integer.MAX_VALUE));
        }

        /** 结构池挑一个（未用满 + 用得最少 + seed 驱动随机），返回下标。 */
        int pickForm(String[] forms) {
            if (relaxed) return rnd.nextInt(forms.length);
            int best = Integer.MAX_VALUE;
            List<Integer> pick = new ArrayList<>();
            for (int i = 0; i < forms.length; i++) {
                int c = used.getOrDefault(forms[i], 0);
                if (c >= limit(forms[i])) continue;
                if (c < best) {
                    best = c;
                    pick.clear();
                }
                if (c == best) pick.add(i);
            }
            // 全池用满（配额合计 < 题数）：放开随机，交由 generate 的兜底段收尾
            return pick.isEmpty() ? rnd.nextInt(forms.length) : pick.get(rnd.nextInt(pick.size()));
        }

        boolean accept(Item it) {
            if (relaxed || it.struct() == null) return true;
            return used.getOrDefault(it.struct(), 0) < limit(it.struct());
        }

        void mark(Item it) {
            if (it.struct() != null) used.merge(it.struct(), 1, Integer::sum);
        }
    }

    // ── 结构池（下标即分支号；池名进 struct 标签，只对内不出参）──
    private static final String[] DEC1_B = {"dec1:add", "dec1:sub"};
    private static final String[] DEC1_A = {"dec1:add", "dec1:sub", "dec1:intdec"};
    private static final String[] ADD3D_F = {"add3d:add", "add3d:sub"};
    private static final String[] MUL1D_B = {"mul1d:2dx1d", "mul1d:3dx1d"};
    private static final String[] MUL1D_A = {"mul1d:mid0", "mul1d:carry"};
    private static final String[] MUL1DORAL_A = {"mul1doral:2dx1d", "mul1doral:1dx2d"};
    private static final String[] MUL2DTENS_A = {"mul2dtens:2dxt", "mul2dtens:tx2d"};
    private static final String[] DIV1D_B = {"div1d:exact", "div1d:rem"};
    private static final String[] DIV1D_A = {"div1d:mid0", "div1d:tail0", "div1d:short", "div1d:rem"};
    /** 脱式·两步骨架池（基础档 8 种）—— 堵"骨架五天连出/同卷出两次"。 */
    private static final String[] MIX3_B = {
        "mix3:a+bxc", "mix3:a-bxc", "mix3:axb+c", "mix3:axb-c",
        "mix3:a-b/c", "mix3:a/b+c", "mix3:(a+b)/c", "mix3:(a-b)xc"};
    /** 脱式·三步骨架池（提高档 9 种：括号在前 / 括号在后 / 双括号 / 连乘除混加减）。 */
    private static final String[] MIX3_A = {
        "mix3:a-b/cxd", "mix3:(a+b)/cxd", "mix3:ax(b/c-d)", "mix3:a/(bxc)+d",
        "mix3:a-(b+c)/d", "mix3:(a-b)xc+d", "mix3:axb+cxd", "mix3:axb-cxd",
        "mix3:(a+b)x(c-d)"};

    /** 支持难度档的类型（三年级计算谱系先落地）；其余类型忽略 level、走原随机逻辑。 */
    private static final Set<String> LEVELED =
        Set.of("mul2d2d", "mul1d", "div1d", "dec1", "add3d", "mul2dtens", "mixops3",
               "mul1doral", "div1doral");

    private Item genOne(String code, GenCtx ctx) {
        // 🔴 脱式恒走骨架轮换生成器（含 steps）：无档缺省 = 三步（类型名即三步），basic = 两步
        if ("mixops3".equals(code)) return genMixops3(ctx, !ctx.basic());
        // 🔴 支持档位的类型：genLeveled 返回 null = 本次摇的不满足档位约束，交给 generate 重摇，
        //    **绝不回落原随机逻辑** —— 回落会漏出不符合档位的题（如基础档出现首位不够除的除法）。
        if (!ctx.level.isEmpty() && LEVELED.contains(code)) {
            return genLeveled(code, ctx);
        }
        return genOne(code, ctx.rnd);
    }

    /**
     * 难度档生成器：{@code basic} 基础 / {@code advanced} 提高。
     *
     * <p>难度靠**客观开关**控制，不靠估计：进位次数 / 因数是否含 0 / 首位是否够除 / 商是否含 0 / 是否退位。
     * 只覆盖三年级计算谱系（每日一练双版本的落点）；其余类型返回 null → 回落原随机逻辑。
     * 生成不满足档位约束时返回 null，由 {@link #generate} 的重试循环重摇。
     *
     * <p>🔴 2026-07-30 审计（45 页人眼目检）三处硬伤在此清偿：①dec1 伪小数（x.0 操作数 / 整数结果）；
     * ②竖式基础档漏出口算级除法（80÷2 / 66÷6）与 1-3 数字池套路（32×22 / 23×23）；
     * ③一行四道同结构（31×80 / 58×80 / 33×80）—— 结构轮换交 {@link GenCtx#pickForm}。
     */
    private Item genLeveled(String code, GenCtx ctx) {
        Random rnd = ctx.rnd;
        boolean adv = ctx.adv();
        switch (code) {
            case "mul1doral" -> {
                // 🔴 basic=原随机（整十整百乘一位）；提高堵"20×2/70×3 送分"（2026-07-30 目检）：
                //   两位数乘一位数口算（非整十）+ 必须进位（个位积≥10）——三上口算真实提高档。
                //   两种乘序轮换（34×7 / 7×34）防一行四道同结构。
                if (!adv) return genOne(code, rnd);
                int a = ri(rnd, 12, 99), b = ri(rnd, 3, 9);
                if (a % 10 == 0 || (a % 10) * b < 10 || a * b > 999) return null;
                int f = ctx.pickForm(MUL1DORAL_A);
                return f == 0 ? qa(a + "×" + b + "＝", String.valueOf(a * b), MUL1DORAL_A[0])
                              : qa(b + "×" + a + "＝", String.valueOf(a * b), MUL1DORAL_A[1]);
            }
            case "div1doral" -> {
                // basic=原随机（整十整百商）；提高：两位数÷一位数整除口算（商非整十，如 78÷6＝13）。
                if (!adv) return genOne(code, rnd);
                int b = ri(rnd, 3, 9), q = ri(rnd, 12, 19);
                if (q % 10 == 0 || b * q > 99) return null;
                return qa((b * q) + "÷" + b + "＝", String.valueOf(q));
            }
            case "mul2d2d" -> {
                // 🔴 基础（2026-07-30 审计修）：原"各位 1-3"数字池太窄，32×22/22×23/23×11/23×23 套路裸奔。
                //   改为各位 1-9 + **交叉积进位 ≤1 次**（"不进位 / 最多一次进位"口径）+ 两因数不同 +
                //   个位非 0（个位 0 = 整十乘法，属口算）——池子放大数十倍，视觉套路消失，难度不变。
                // 提高（2026-07-30 放宽）：各位 4-9 数域太窄，一卷里反复出 75×76/77×75 雷同；
                //   改为各位 3-9 + 个位积进位（(a%10)*(b%10)≥10）+ a≠b —— 数域变大，进位保证不丢。
                if (!adv) {
                    int a1 = ri(rnd, 1, 9), a0 = ri(rnd, 1, 9), b1 = ri(rnd, 1, 9), b0 = ri(rnd, 1, 9);
                    int carries = (a1 * b1 >= 10 ? 1 : 0) + (a1 * b0 >= 10 ? 1 : 0)
                                + (a0 * b1 >= 10 ? 1 : 0) + (a0 * b0 >= 10 ? 1 : 0);
                    if (carries > 1) return null;
                    int x = a1 * 10 + a0, y = b1 * 10 + b0;
                    if (x == y) return null;
                    return qa(x + "×" + y + "＝", String.valueOf(x * y));
                }
                int a = ri(rnd, 3, 9) * 10 + ri(rnd, 3, 9);
                int b = ri(rnd, 3, 9) * 10 + ri(rnd, 3, 9);
                if (a == b || (a % 10) * (b % 10) < 10) return null;
                return qa(a + "×" + b + "＝", String.valueOf(a * b));
            }
            case "mul2dtens" -> {
                // 🔴 basic = 原随机逻辑（零回归，G 回归线）；提高堵"61×10 送分"：
                //   乘数排除 10（20-90 整十），被乘数个位非 0（必须真进位算两步）；
                //   🔴 两种乘序轮换（81×30 / 30×81）——堵"一行四道全是两位数×整十"（2026-07-30 审计）。
                if (!adv) return genOne(code, rnd);
                int a = ri(rnd, 12, 99), b = ri(rnd, 2, 9) * 10;
                if (a % 10 == 0) return null;
                int f = ctx.pickForm(MUL2DTENS_A);
                return f == 0 ? qa(a + "×" + b + "＝", String.valueOf(a * b), MUL2DTENS_A[0])
                              : qa(b + "×" + a + "＝", String.valueOf(a * b), MUL2DTENS_A[1]);
            }
            case "mixops3" -> {
                // 基础=两步（数域 100 内）；提高=三步跨级（含括号/两级运算）。骨架轮换 + steps 见 genMixops3
                return genMixops3(ctx, adv);
            }
            case "mul1d" -> {
                if (!adv) {
                    // 基础（审计修）：原"各位 1-3 × 乘数 2-3"同属套路池；改为各位 1-9（无 0，0 是提高档陷阱）、
                    //   乘数 2-5、**进位 ≤1 次**，两位/三位数两种形态轮换。
                    int f = ctx.pickForm(MUL1D_B);
                    int b = ri(rnd, 2, 5);
                    int a = f == 0 ? ri(rnd, 12, 98) : ri(rnd, 112, 987);
                    int carries = 0;
                    for (int n = a; n > 0; n /= 10) {
                        int d = n % 10;
                        if (d == 0) return null;
                        if (d * b >= 10) carries++;
                    }
                    if (carries > 1) return null;
                    return qa(a + "×" + b + "＝", String.valueOf(a * b), MUL1D_B[f]);
                }
                int f = ctx.pickForm(MUL1D_A);
                if (f == 0) {                                  // 因数中间有 0（409×5）
                    int a = ri(rnd, 1, 9) * 100 + ri(rnd, 1, 9), b = ri(rnd, 4, 9);
                    return qa(a + "×" + b + "＝", String.valueOf(a * b), MUL1D_A[0]);
                }
                int a = ri(rnd, 6, 9) * 100 + ri(rnd, 6, 9) * 10 + ri(rnd, 6, 9);
                int b = ri(rnd, 6, 9);                         // 连续进位
                return qa(a + "×" + b + "＝", String.valueOf(a * b), MUL1D_A[1]);
            }
            case "div1d" -> {
                if (!adv) {
                    // 🔴 基础（2026-07-30 审计修）：原"两位数÷一位数整除"漏出 80÷2 / 66÷6 / 55÷5 / 96÷6
                    //   这类口算级题（放"列竖式"零训练价值）。现在必须"值得列竖式"：
                    //   f0 = 三位数÷一位数整除（首位够除 → 商三位；商无 0 位；**至少一步有余数往下带**；
                    //        排除"被除数是除数的整十倍数"如 240÷2）；
                    //   f1 = 有余数（商 ≥ 两位）—— 余数本身就要求竖式。
                    int f = ctx.pickForm(DIV1D_B);
                    int b = ri(rnd, 2, 9);
                    if (f == 0) {
                        int q = ri(rnd, Math.max(100, (100 + b - 1) / b), 999 / b);
                        int d = b * q;
                        if (q < 100 || d < 100 || d > 999) return null;
                        if (firstDigit(d) < b) return null;                  // 首位不够除 = 提高档
                        if (hasZeroDigit(q)) return null;                    // 商含 0 = 提高档
                        if (d % 10 == 0 && (d / 10) % b == 0) return null;   // 除数的整十倍数 = 口算级
                        if (divCarrySteps(d, b) == 0) return null;           // 每位都整除（369÷3）= 口算级
                        return qa(d + "÷" + b + "＝", String.valueOf(q), DIV1D_B[0]);
                    }
                    int q = ri(rnd, 11, 99), r = ri(rnd, 1, b - 1);
                    int d = b * q + r;
                    if (d > 999 || hasZeroDigit(q)) return null;             // 商末尾/中间 0 = 提高档
                    return qa(d + "÷" + b + "＝", q + "……" + r, DIV1D_B[1]);
                }
                // 提高 = 四种错点形态轮换：商中间有 0 / 商末尾有 0 / 首位不够除 / 三位数有余数
                int f = ctx.pickForm(DIV1D_A);
                int b = ri(rnd, 2, 9);
                switch (f) {
                    case 0 -> {                                              // 商中间有 0（612÷6＝102）
                        int q = ri(rnd, 1, 9) * 100 + ri(rnd, 1, 9);
                        int d = b * q;
                        if (d > 999) return null;
                        // 🔴 难度倒挂修（2026-07-30 复审）：÷2 可心算折半（610÷2 实锤）。
                        //   注意此形态不叠加 divCarrySteps 闸——商中间 0 的合法样本里"每位整除"占比高，
                        //   叠闸会稀到触发放宽兜底、四形态覆盖断裂（终链测试实锤回滚）
                        if (b < 3) return null;
                        return qa(d + "÷" + b + "＝", String.valueOf(q), DIV1D_A[0]);
                    }
                    case 1 -> {                                              // 商末尾有 0（840÷4＝210）
                        int q = ri(rnd, 11, 49) * 10;
                        int d = b * q;
                        if (d > 999) return null;
                        // 同上：堵 300÷2/420÷2/880÷8（复审实锤提高版除法反比基础简单）
                        if (b < 3 || divCarrySteps(d, b) == 0) return null;
                        return qa(d + "÷" + b + "＝", String.valueOf(q), DIV1D_A[1]);
                    }
                    case 2 -> {                                              // 首位不够除（372÷6＝62）
                        int q = ri(rnd, 11, 99);
                        int d = b * q;
                        if (d < 100 || d > 999 || firstDigit(d) >= b) return null;
                        return qa(d + "÷" + b + "＝", String.valueOf(q), DIV1D_A[2]);
                    }
                    default -> {                                             // 三位数有余数
                        if (b < 3) return null;
                        int q = ri(rnd, 21, 110), r = ri(rnd, 1, b - 1);
                        int d = b * q + r;
                        if (d < 100 || d > 999) return null;
                        return qa(d + "÷" + b + "＝", q + "……" + r, DIV1D_A[3]);
                    }
                }
            }
            case "dec1" -> {
                // 以「十分之一」为整数单位算，规避浮点误差；dec1() 负责回显小数。
                // 🔴 2026-07-30 审计铁律（两条，构造式保证不靠碰运气）：
                //   ①**操作数十分位 ≠ 0** —— 禁 2.0＋3.8 / 6.0－4.0 这类伪小数（等价整数运算，还教错"小数末尾0"）；
                //   ②**结果十分位 ≠ 0** —— 禁答案 10.0 / 4.0 / 2.0（三下未学小数性质不能化简，家长照着判错）。
                // 基础 = 个位数.一位、不进不退（3.5＋4.1）；提高 = 可到两位数.一位且必含进/退位（14.6－8.9）。
                if (!adv) {
                    int f = ctx.pickForm(DEC1_B);
                    if (f == 0) {                                            // 不进位加
                        int ad = ri(rnd, 1, 8), bd = ri(rnd, 1, 9 - ad);     // 十分位和 ≤9 且 ≥2
                        int ai = ri(rnd, 1, 8), bi = ri(rnd, 1, 9 - ai);     // 整数部分和 ≤9
                        int a = ai * 10 + ad, b = bi * 10 + bd;
                        return qa(dec1(a) + "＋" + dec1(b) + "＝", dec1(a + b), DEC1_B[0]);
                    }
                    int ad = ri(rnd, 2, 9), bd = ri(rnd, 1, ad - 1);         // 不退位减，差的十分位 ≥1
                    int ai = ri(rnd, 2, 9), bi = ri(rnd, 1, ai);
                    int a = ai * 10 + ad, b = bi * 10 + bd;
                    return qa(dec1(a) + "－" + dec1(b) + "＝", dec1(a - b), DEC1_B[1]);
                }
                int f = ctx.pickForm(DEC1_A);
                if (f == 0) {                                                // 进位加（十分位和 11-18）
                    int ad = ri(rnd, 2, 9), bd = ri(rnd, 11 - ad, 9);
                    if (ad + bd < 11) return null;                           // 和 =10 会出 x.0 结果，剔除
                    int ai = ri(rnd, 2, 19), bi = ri(rnd, 2, 19);
                    int a = ai * 10 + ad, b = bi * 10 + bd;
                    return qa(dec1(a) + "＋" + dec1(b) + "＝", dec1(a + b), DEC1_A[0]);
                }
                if (f == 1) {                                                // 退位减（14.6－8.9）
                    int ad = ri(rnd, 1, 8), bd = ri(rnd, ad + 1, 9);         // 十分位不够减 → 必退位
                    int bi = ri(rnd, 2, 9), ai = ri(rnd, bi + 1, 19);
                    int a = ai * 10 + ad, b = bi * 10 + bd;
                    return qa(dec1(a) + "－" + dec1(b) + "＝", dec1(a - b), DEC1_A[1]);
                }
                int ai = ri(rnd, 3, 19), bi = ri(rnd, 1, ai - 1), bd = ri(rnd, 1, 9);   // 整数减小数（12－4.7）
                int b = bi * 10 + bd;
                return qa(ai + "－" + dec1(b) + "＝", dec1(ai * 10 - b), DEC1_A[2]);
            }
            case "add3d" -> {
                int f = ctx.pickForm(ADD3D_F);
                if (!adv) {
                    if (f == 0) {                                            // 不进位加
                        int a = d3(rnd, 1, 4), b = d3(rnd, 1, 4);
                        return carryAdd(a, b) > 0 ? null
                            : qa(a + "＋" + b + "＝", String.valueOf(a + b), ADD3D_F[0]);
                    }
                    int a = d3(rnd, 5, 9), b = d3(rnd, 1, 4);                // 不退位减
                    return (a <= b || borrowSub(a, b) > 0) ? null
                        : qa(a + "－" + b + "＝", String.valueOf(a - b), ADD3D_F[1]);
                }
                if (f == 0) {                                                // 连续进位加
                    int a = ri(rnd, 150, 780), b = ri(rnd, 150, 999 - a);
                    return (b < 150 || carryAdd(a, b) < 2) ? null
                        : qa(a + "＋" + b + "＝", String.valueOf(a + b), ADD3D_F[0]);
                }
                int a = ri(rnd, 300, 999), b = ri(rnd, 110, a - 50);         // 连续退位减
                return borrowSub(a, b) < 2 ? null
                    : qa(a + "－" + b + "＝", String.valueOf(a - b), ADD3D_F[1]);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * 脱式混合运算（mixops3，三下两级运算落点）：基础 = 两步 8 骨架、提高 = 三步 9 骨架。
     *
     * <p>🔴 2026-07-30 审计修（模板化失控）：原来基础/提高各只有 5 个骨架且**每次纯随机**，导致
     * "N÷(a×b)+M" 五天连出、第 10 天同卷出两次。现在骨架池扩到 8/9 种，并由 {@link GenCtx#pickForm}
     * 在一次 generate 内**挑用得最少的骨架** —— 一次出 4 道必是 4 种骨架（跨天重摇由脚本侧闸兜底）。
     *
     * <p>硬约束（全部程序保证，不靠碰运气）：<b>全整数</b>／<b>每步除法必整除</b>／<b>中间结果 ∈[2,999]</b>／
     * <b>除数一位数</b>（{@code A÷(B×C)} 形态里 B×C ≤ 9）／<b>括号内运算结果 ≥3</b>／<b>乘数 ≥2</b>
     * （基础再抬到"因数 ≥3 且积 ≥12"，堵 {@code 2×3＋87} / {@code 8×(25÷5－4)} / {@code (5＋5)÷5} 级琐碎题）。
     * 提高档操作数整档加大：被除数三位数、括号内两位数运算。
     *
     * @param three true=三步跨级形态（提高档 / 无档缺省）；false=两步形态（基础档，结果 ≤100）
     */
    private Item genMixops3(GenCtx ctx, boolean three) {
        String[] pool = three ? MIX3_A : MIX3_B;
        int f = ctx.pickForm(pool);
        return three ? genMix3(ctx.rnd, f, pool[f]) : genMix2(ctx.rnd, f, pool[f]);
    }

    /** 脱式·两步（基础档）8 骨架，带 steps 分步串。 */
    private Item genMix2(Random rnd, int f, String s) {
        switch (f) {
            case 0 -> {                                          // A＋B×C（乘在后，练运算顺序）
                int b = ri(rnd, 3, 9), c = ri(rnd, 3, 9), p = b * c;
                if (p < 12) return null;
                int a = ri(rnd, 11, 100 - p);
                if (a < 11) return null;
                return step(a + "＋" + b + "×" + c + "＝", String.valueOf(a + p), s,
                    a + "＋" + p, String.valueOf(a + p));
            }
            case 1 -> {                                          // A－B×C
                int b = ri(rnd, 3, 9), c = ri(rnd, 3, 9), p = b * c;
                if (p < 12) return null;
                int a = ri(rnd, p + 11, 99);
                if (a > 99) return null;
                return step(a + "－" + b + "×" + c + "＝", String.valueOf(a - p), s,
                    a + "－" + p, String.valueOf(a - p));
            }
            case 2 -> {                                          // A×B＋C
                int a = ri(rnd, 3, 9), b = ri(rnd, 3, 9), p = a * b;
                if (p < 12) return null;
                int c = ri(rnd, 11, 100 - p);
                if (c < 11) return null;
                return step(a + "×" + b + "＋" + c + "＝", String.valueOf(p + c), s,
                    p + "＋" + c, String.valueOf(p + c));
            }
            case 3 -> {                                          // A×B－C
                int a = ri(rnd, 3, 9), b = ri(rnd, 3, 9), p = a * b;
                if (p < 24) return null;                         // 保证减数 ≥11 且差 ≥2
                int c = ri(rnd, 11, p - 2);
                return step(a + "×" + b + "－" + c + "＝", String.valueOf(p - c), s,
                    p + "－" + c, String.valueOf(p - c));
            }
            case 4 -> {                                          // A－B÷C
                int c = ri(rnd, 2, 9), q = ri(rnd, 3, 9), b = c * q;
                if (b < 12) return null;                         // 被除数至少两位数
                int a = ri(rnd, q + 11, 99);
                return step(a + "－" + b + "÷" + c + "＝", String.valueOf(a - q), s,
                    a + "－" + q, String.valueOf(a - q));
            }
            case 5 -> {                                          // A÷B＋C
                int b = ri(rnd, 2, 9), q = ri(rnd, 4, 12), d = b * q;
                if (d < 12 || d > 99) return null;
                int c = ri(rnd, 11, 100 - q);
                return step(d + "÷" + b + "＋" + c + "＝", String.valueOf(q + c), s,
                    q + "＋" + c, String.valueOf(q + c));
            }
            case 6 -> {                                          // (A＋B)÷C（括号在前）
                int c = ri(rnd, 2, 9), q = ri(rnd, 3, 99 / c), sum = c * q;
                if (sum < 20) return null;                       // 括号内两位数加法，堵 (5＋5)÷5
                int x = ri(rnd, 5, sum - 5), y = sum - x;
                if (x == y || y < 5) return null;                // 三数雷同一眼算，剔除
                return step("(" + x + "＋" + y + ")÷" + c + "＝", String.valueOf(q), s,
                    sum + "÷" + c, String.valueOf(q));
            }
            default -> {                                         // (A－B)×C
                int c = ri(rnd, 3, 9), diff = ri(rnd, 3, 12), p = diff * c;
                if (p < 12 || p > 100) return null;
                int a = ri(rnd, diff + 11, 99), b = a - diff;
                if (b < 11) return null;
                return step("(" + a + "－" + b + ")×" + c + "＝", String.valueOf(p), s,
                    diff + "×" + c, String.valueOf(p));
            }
        }
    }

    /** 脱式·三步（提高档）9 骨架，带 steps 分步串；操作数整档加大（被除数三位数 / 括号内两位数运算）。 */
    private Item genMix3(Random rnd, int f, String s) {
        switch (f) {
            case 0 -> {                                          // A－B÷C×D（连乘除混加减）
                int c = ri(rnd, 2, 9), q = ri(rnd, 12, 60), b = c * q;
                if (b < 100 || b > 999) return null;             // 被除数三位数
                int d = ri(rnd, 2, 9);
                if (d == c) return null;                         // 🔴 ÷C×D 同数相消=退化一步（verifier S5）
                int p = q * d;
                if (p > 900) return null;
                int a = ri(rnd, p + 11, 999);
                return step(a + "－" + b + "÷" + c + "×" + d + "＝", String.valueOf(a - p), s,
                    a + "－" + q + "×" + d, a + "－" + p, String.valueOf(a - p));
            }
            case 1 -> {                                          // (A＋B)÷C×D（括号在前）
                int c = ri(rnd, 2, 9), q = ri(rnd, 11, 40), sum = c * q;
                if (sum < 22 || sum > 300) return null;
                int d = ri(rnd, 2, 9);
                if (d == c || q * d > 999) return null;
                int x = ri(rnd, 11, sum - 11), y = sum - x;
                if (x == y || y < 11) return null;
                return step("(" + x + "＋" + y + ")÷" + c + "×" + d + "＝", String.valueOf(q * d), s,
                    sum + "÷" + c + "×" + d, q + "×" + d, String.valueOf(q * d));
            }
            case 2 -> {                                          // A×(B÷C－D)（括号在后）
                int c = ri(rnd, 2, 9), q = ri(rnd, 12, 40), b = c * q;
                if (b < 100 || b > 999) return null;
                int d = ri(rnd, 2, q - 3), inner = q - d;
                int a = ri(rnd, 3, 9);
                if (inner < 3 || a * inner > 999 || a * inner < 12) return null;
                return step(a + "×(" + b + "÷" + c + "－" + d + ")＝", String.valueOf(a * inner), s,
                    a + "×(" + q + "－" + d + ")", a + "×" + inner, String.valueOf(a * inner));
            }
            case 3 -> {                                          // A÷(B×C)＋D（🔴 括号内积 ≤9）
                int b = ri(rnd, 2, 4), c = ri(rnd, 2, 9 / b), m = b * c;
                if (m < 4 || m > 9) return null;
                int q = ri(rnd, 12, 99), dd = m * q;
                if (dd < 100 || dd > 999) return null;
                int d = ri(rnd, 11, 99);
                return step(dd + "÷(" + b + "×" + c + ")＋" + d + "＝", String.valueOf(q + d), s,
                    dd + "÷" + m + "＋" + d, q + "＋" + d, String.valueOf(q + d));
            }
            case 4 -> {                                          // A－(B＋C)÷D
                int dv = ri(rnd, 2, 9), q = ri(rnd, 11, 40), sum = dv * q;
                if (sum < 22 || sum > 300) return null;
                int x = ri(rnd, 11, sum - 11), y = sum - x;
                if (x == y || y < 11) return null;
                int a = ri(rnd, q + 11, 999);
                return step(a + "－(" + x + "＋" + y + ")÷" + dv + "＝", String.valueOf(a - q), s,
                    a + "－" + sum + "÷" + dv, a + "－" + q, String.valueOf(a - q));
            }
            case 5 -> {                                          // (A－B)×C＋D
                int diff = ri(rnd, 11, 40), c = ri(rnd, 2, 9), p = diff * c;
                if (p > 900) return null;
                int a = ri(rnd, diff + 11, 99), b = a - diff;
                if (b < 11) return null;
                int d = ri(rnd, 11, 99);
                if (p + d > 999) return null;
                return step("(" + a + "－" + b + ")×" + c + "＋" + d + "＝", String.valueOf(p + d), s,
                    diff + "×" + c + "＋" + d, p + "＋" + d, String.valueOf(p + d));
            }
            case 6 -> {                                          // A×B＋C×D（两级两次乘）
                int a = ri(rnd, 11, 40), b = ri(rnd, 2, 9), p1 = a * b;
                int c = ri(rnd, 11, 40), d = ri(rnd, 2, 9), p2 = c * d;
                if (a == c || p1 + p2 > 999) return null;
                return step(a + "×" + b + "＋" + c + "×" + d + "＝", String.valueOf(p1 + p2), s,
                    p1 + "＋" + c + "×" + d, p1 + "＋" + p2, String.valueOf(p1 + p2));
            }
            case 7 -> {                                          // A×B－C×D
                int a = ri(rnd, 11, 40), b = ri(rnd, 2, 9), p1 = a * b;
                int c = ri(rnd, 11, 40), d = ri(rnd, 2, 9), p2 = c * d;
                if (a == c || p1 - p2 < 11 || p1 > 999) return null;
                return step(a + "×" + b + "－" + c + "×" + d + "＝", String.valueOf(p1 - p2), s,
                    p1 + "－" + c + "×" + d, p1 + "－" + p2, String.valueOf(p1 - p2));
            }
            default -> {                                         // (A＋B)×(C－D)（双括号）
                int x = ri(rnd, 11, 40), y = ri(rnd, 11, 40), sum = x + y;
                int c = ri(rnd, 13, 40), d = ri(rnd, 2, c - 3), inner = c - d;
                if (inner < 3 || sum * inner > 999) return null;
                return step("(" + x + "＋" + y + ")×(" + c + "－" + d + ")＝", String.valueOf(sum * inner), s,
                    sum + "×(" + c + "－" + d + ")", sum + "×" + inner, String.valueOf(sum * inner));
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

    /** 是否含 0 数位（商含 0 = 提高档错点，基础档剔除）。 */
    private static boolean hasZeroDigit(int n) {
        for (int x = n; x > 0; x /= 10) {
            if (x % 10 == 0) return true;
        }
        return false;
    }

    /**
     * 竖式除法里"本位有余数、往下一位带"的步数。
     *
     * <p>0 = 每一位都刚好整除（{@code 369÷3}、{@code 80÷2}、{@code 222÷2}）—— 这类题口算就能出结果，
     * 放在"列竖式计算"零训练价值（2026-07-30 审计实锤），基础档据此剔除。
     */
    private static int divCarrySteps(int d, int b) {
        int cnt = 0, cur = 0;
        for (char ch : String.valueOf(d).toCharArray()) {
            cur = cur * 10 + (ch - '0');
            int r = cur % b;
            if (r != 0 && cur >= b) cnt++;
            cur = r;
        }
        return cnt;
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

    private Item genOne(String code, Random rnd) {
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
                    // 脱式消费方需分步（2026-07-30 审计批：mixops3 有 steps 而 paren 没有=解析体例不齐）
                    yield step(a + "－(" + b + "＋" + c + ")＝", String.valueOf(a - b - c), "A－(B＋C)",
                        a + "－" + (b + c), String.valueOf(a - b - c));
                }
                // 琐碎闸（2026-07-30 审计批）：括号内和≥20、两加数≥2 且不等、商≥3——堵 (11＋4)÷5、(1＋14)÷5
                int c = ri(rnd, 3, 9), q = ri(rnd, Math.max(3, (20 + c - 1) / c), 9);
                int s = c * q, b2 = ri(rnd, 2, s - 2);
                if (b2 * 2 == s) yield null;
                yield step("(" + (s - b2) + "＋" + b2 + ")÷" + c + "＝", String.valueOf(q), "(A＋B)÷C",
                    s + "÷" + c, String.valueOf(q));
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
            // 无档缺省 = 三步跨级形态（类型名即三步；两步形态走 level=basic）。
            // 🔴 正常路径由 genOne(code, GenCtx) 截走（带骨架轮换）；此处是无 ctx 的兜底入口，
            //    临时 ctx（count=1）等价于"骨架纯随机"，保留防未知调用方裸调 genOne(code, rnd) 撞未知类型。
            case "mixops3" -> genMixops3(new GenCtx(rnd, "", 1), true);
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

    /** 无分步、不参与同质闸的普通题。 */
    private static Item qa(String q, String a) {
        return new Item(q, a, List.of(), null);
    }

    /** 无分步、带结构签名（进同质闸配额）的题。 */
    private static Item qa(String q, String a, String struct) {
        return new Item(q, a, List.of(), struct);
    }

    /** 带分步化简串的题（脱式）：steps 末项 = 终值，与 a 一致。 */
    private static Item step(String q, String a, String struct, String... steps) {
        return new Item(q, a, List.of(steps), struct);
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
