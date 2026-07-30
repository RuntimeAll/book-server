package org.dromara.book.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计算出题器质量闸自验 —— PRD-013 三升四每日一练 2026-07-30「45 页人眼审计」的机器化清偿。
 *
 * <p>纯 new 实例（renderer 注 null，只测 {@code generateItems}，不起 Spring 上下文）。覆盖：
 * <ol>
 *   <li><b>dec1 伪小数</b>：操作数与结果的十分位均 ≠0（禁 2.0＋3.8、禁答案 10.0/4.0），答案独立重算；</li>
 *   <li><b>脱式</b>：steps 分步链逐步求值正确、单次 4 道骨架互异、骨架池够大、琐碎闸（中间结果/乘数/
 *       括号内结果阈值）、除法整除；</li>
 *   <li><b>竖式</b>：基础档除法禁口算级（80÷2/66÷6/369÷3）、商位数/余数自洽、mul2d2d 数字池破套路；</li>
 *   <li><b>学段红线</b>：本产品全类型除数一位数、小数只一位、结果非负、答案与题面自洽；</li>
 *   <li><b>同质闸</b>：同 group 内同结构 ≤60%、整数减小数 ≤2 道；</li>
 *   <li><b>确定性</b>：同 seed 同输出（字符串 seed 走 hashCode 通路）。</li>
 * </ol>
 */
@Tag("dev")
class OralCalcServiceTest {

    /** renderer 只在 export 用；本类只走 generateItems 纯计算路径。 */
    private final OralCalcService svc = new OralCalcService(null);

    // 全角运算符常量（与出题器题面一致：＋U+FF0B / －U+FF0D / ×U+00D7 / ÷U+00F7 / ＝U+FF1D / …U+2026）
    private static final char ADD = '＋';           // ＋
    private static final char SUB = '－';           // －
    private static final char MUL = '×';           // ×
    private static final char DIV = '÷';           // ÷
    private static final String EQ = "＝";          // ＝
    private static final String REM = "……";   // ……（有余数答案的商余分隔）

    /** 三升四每日一练实际用到的全部类型（配方正本 = 每日一练产物/_模板/punch_recipe.py）。 */
    private static final List<String> PUNCH_TYPES = List.of(
        "multable", "divtable", "add3d", "mul1doral", "div1doral", "dec1",
        "mul2dtens", "mul1d", "div1d", "mul2d2d", "mixops3");

    private static final Pattern DECIMAL = Pattern.compile("\\d+\\.\\d+");

    // ───────────────── ① dec1：伪小数根除 ─────────────────

    @Test
    void dec1NeverEmitsFakeDecimals() {
        for (String level : List.of("basic", "advanced")) {
            int intMinusDecMax = 0;
            for (int i = 0; i < 200; i++) {
                List<Map<String, Object>> its = items("dec1", level, 5, "dec" + level + i);
                assertEquals(5, its.size(), "题数须精确");
                int intMinusDec = 0;
                for (Map<String, Object> it : its) {
                    String q = (String) it.get("q");
                    String a = (String) it.get("a");
                    int[] parsed = parseDec1(q, a, level);       // 内含答案独立重算 + 十分位断言
                    if (parsed[0] % 10 == 0) intMinusDec++;      // 左操作数是整数 = 整数减小数型
                }
                intMinusDecMax = Math.max(intMinusDecMax, intMinusDec);
            }
            // 🔴 同质硬配额：整数减小数一组 ≤2 道（审计：4－2.4/3－1.3/6－2.7/6－1.3 一行四道）
            assertTrue(intMinusDecMax <= 2, level + " 整数减小数一组超 2 道: " + intMinusDecMax);
        }
    }

    /** 解析 {@code x±y＝} 并断言：一位小数、十分位≠0、结果非整数、答案与独立重算一致。返回 {十分位单位的 x, y}。 */
    private int[] parseDec1(String q, String a, String level) {
        assertTrue(q.endsWith(EQ), "题面须以＝结尾: " + q);
        String body = q.substring(0, q.length() - 1);
        int idx = -1;
        char op = 0;
        for (int i = 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == ADD || c == SUB) {
                idx = i;
                op = c;
                break;
            }
        }
        assertTrue(idx > 0, "一位小数题须是两数加减: " + q);
        String xs = body.substring(0, idx), ys = body.substring(idx + 1);
        assertOneDecimalPlaceNonZero(xs, q);
        assertOneDecimalPlaceNonZero(ys, q);
        int x = tenths(xs), y = tenths(ys);
        int expect = op == ADD ? x + y : x - y;
        assertTrue(expect > 0, "结果不得为负/零: " + q);
        assertEquals((expect / 10) + "." + (expect % 10), a, "答案与独立重算不符: " + q);
        assertTrue(a.contains("."), "结果必须是小数: " + q + a);
        assertNotEquals('0', a.charAt(a.length() - 1), "结果十分位为 0（伪小数，家长会判错）: " + q + a);
        if ("basic".equals(level)) {
            assertTrue(x < 100 && y < 100, "基础档操作数须个位数.一位: " + q);
            if (op == ADD) {
                assertTrue(x % 10 + y % 10 <= 9, "基础档加法不进位: " + q);
            } else {
                assertTrue(x % 10 > y % 10, "基础档减法不退位且差的十分位≠0: " + q);
            }
        } else if (op == ADD) {
            assertTrue(x % 10 + y % 10 >= 11, "提高档加法必进位且结果十分位≠0: " + q);
        } else {
            assertTrue(x % 10 < y % 10, "提高档减法必退位: " + q);
        }
        return new int[]{x, y};
    }

    /** 小数字面量：恰一位小数且十分位≠0（整数字面量放行——整数减小数是三下正式形态）。 */
    private void assertOneDecimalPlaceNonZero(String num, String where) {
        if (num.indexOf('.') < 0) {
            assertTrue(num.matches("\\d+"), "操作数非法: " + num + " @" + where);
            return;
        }
        String frac = num.substring(num.indexOf('.') + 1);
        assertEquals(1, frac.length(), "只允许一位小数: " + where);
        assertNotEquals('0', frac.charAt(0), "操作数十分位为 0（伪小数 x.0）: " + where);
    }

    // ───────────────── ② 脱式：分步 + 骨架 + 琐碎闸 ─────────────────

    @Test
    @SuppressWarnings("unchecked")
    void stepwiseHasStepsAndDistinctSkeletons() {
        for (String level : List.of("basic", "advanced")) {
            Set<String> allSkeletons = new LinkedHashSet<>();
            for (int i = 0; i < 100; i++) {
                List<Map<String, Object>> its = items("mixops3", level, 4, "st" + level + i);
                assertEquals(4, its.size());
                Set<String> perCall = new LinkedHashSet<>();
                for (Map<String, Object> it : its) {
                    String q = (String) it.get("q");
                    String a = (String) it.get("a");
                    List<String> steps = (List<String>) it.get("steps");
                    assertNotNull(steps, "脱式必带 steps: " + q);
                    assertFalse(steps.isEmpty(), "steps 不得为空: " + q);

                    Expr eq = new Expr(q);
                    int value = eq.eval();
                    assertEquals(Integer.parseInt(a), value, "答案与题面不符: " + q + a);
                    assertEquals(eq.ops(), steps.size(), "steps 步数须等于运算步数: " + q + steps);
                    checkGates(eq, level, q);

                    int expectOps = eq.ops();
                    for (String st : steps) {
                        assertFalse(st.contains(EQ), "steps 各项不带＝前缀: " + st);
                        Expr es = new Expr(st);
                        assertEquals(value, es.eval(), "分步求值链断裂: " + q + " -> " + st);
                        assertEquals(--expectOps, es.ops(), "分步未逐步化简一步: " + q + " -> " + st);
                        checkGates(es, level, st);
                    }
                    assertEquals(a, steps.get(steps.size() - 1), "steps 末项须为终值: " + q + steps);
                    assertEquals(0, expectOps, "末项须化简到纯数值: " + q + steps);
                    assertEquals("basic".equals(level) ? 2 : 3, steps.size(),
                        "基础=两步 / 提高=三步: " + q + steps);
                    perCall.add(skeleton(q));
                }
                assertEquals(4, perCall.size(), "单次 4 道骨架必互异: " + perCall);
                allSkeletons.addAll(perCall);
            }
            int min = "basic".equals(level) ? 5 : 6;
            assertTrue(allSkeletons.size() >= min,
                level + " 骨架池须 ≥" + min + " 种，实测 " + allSkeletons.size() + ": " + allSkeletons);
        }
    }

    /** 骨架签名：数字全抹成 N（审计时人眼看到的"同一个模板"就是这个）。 */
    private static String skeleton(String q) {
        return q.replace(EQ, "").replaceAll("\\d+", "N");
    }

    /** 琐碎闸 + 学段红线：中间结果 ∈[2,999]、括号内结果 ≥3、乘数 ≥2（基础 ≥3 且积 ≥12）、除数一位数。 */
    private void checkGates(Expr e, String level, String src) {
        for (int r : e.results) {
            assertTrue(r >= 2 && r <= 999, "中间/最终结果须 ∈[2,999]（琐碎/越界）: " + src + " -> " + r);
        }
        for (int p : e.parens) {
            assertTrue(p >= 3, "括号内运算结果 <3（如 25÷5－4=1，一眼算）: " + src);
        }
        for (int[] m : e.muls) {
            int lo = Math.min(m[0], m[1]);
            assertTrue(lo >= 2, "乘数 <2: " + src);
            if ("basic".equals(level)) {
                assertTrue(lo >= 3 && m[0] * m[1] >= 12, "基础档乘法过琐碎（2×3 级）: " + src);
            }
        }
        for (int d : e.divisors) {
            assertTrue(d >= 2 && d <= 9, "🔴 学段红线：除数只能一位数: " + src + " -> " + d);
        }
    }

    // ───────────────── ③ 竖式：除法禁口算级 / 乘法破套路 ─────────────────

    @Test
    void verticalDivisionBasicIsWorthLongDivision() {
        Set<String> forms = new LinkedHashSet<>();
        for (int i = 0; i < 200; i++) {
            for (Map<String, Object> it : items("div1d", "basic", 4, "dvb" + i)) {
                String q = (String) it.get("q");
                String a = (String) it.get("a");
                int[] db = parseDivQ(q);
                int d = db[0], b = db[1];
                assertTrue(b >= 2 && b <= 9, "除数只能一位数: " + q);
                assertTrue(d >= 10 && d <= 999, "被除数须两/三位数: " + q);
                if (a.contains(REM)) {
                    int j = a.indexOf(REM);
                    int quo = Integer.parseInt(a.substring(0, j));
                    int r = Integer.parseInt(a.substring(j + REM.length()));
                    assertEquals(d, b * quo + r, "余数式不成立: " + q + a);
                    assertTrue(r >= 1 && r < b, "余数须 ∈[1,除数-1]: " + q + a);
                    assertTrue(quo >= 10, "商须两位以上（表内除法级不进竖式）: " + q + a);
                    assertFalse(String.valueOf(quo).contains("0"), "商含 0 属提高档: " + q + a);
                    forms.add("rem");
                } else {
                    int quo = Integer.parseInt(a);
                    assertEquals(d, b * quo, "整除不成立: " + q + a);
                    assertTrue(quo >= 10, "商须两位以上: " + q + a);
                    assertTrue(d >= 100, "基础整除须三位数被除数（两位数整除=口算级 80÷2/66÷6）: " + q);
                    assertTrue(firstDigit(d) >= b, "基础档首位须够除（不够除属提高档）: " + q);
                    assertFalse(String.valueOf(quo).contains("0"), "商含 0 属提高档: " + q + a);
                    assertFalse(d % 10 == 0 && (d / 10) % b == 0,
                        "被除数是除数的整十倍数=口算级: " + q);
                    assertTrue(divCarrySteps(d, b) > 0,
                        "每位都整除（369÷3 型）=口算级，无竖式训练价值: " + q);
                    forms.add("exact");
                }
            }
        }
        assertEquals(Set.of("exact", "rem"), forms, "基础档除法两种形态都要出（整除 + 有余数）");
    }

    @Test
    void verticalDivisionAdvancedIsHarderThanBasic() {
        Set<String> traps = new LinkedHashSet<>();
        for (int i = 0; i < 200; i++) {
            for (Map<String, Object> it : items("div1d", "advanced", 4, "dva" + i)) {
                String q = (String) it.get("q");
                String a = (String) it.get("a");
                int[] db = parseDivQ(q);
                int d = db[0], b = db[1];
                assertTrue(b >= 2 && b <= 9, "除数只能一位数: " + q);
                assertTrue(d >= 100 && d <= 999, "提高档被除数须三位数: " + q);
                boolean rem = a.contains(REM);
                int quo;
                if (rem) {
                    int j = a.indexOf(REM);
                    quo = Integer.parseInt(a.substring(0, j));
                    int r = Integer.parseInt(a.substring(j + REM.length()));
                    assertEquals(d, b * quo + r, "余数式不成立: " + q + a);
                    assertTrue(r >= 1 && r < b, "余数须 ∈[1,除数-1]: " + q + a);
                    traps.add("rem");
                } else {
                    quo = Integer.parseInt(a);
                    assertEquals(d, b * quo, "整除不成立: " + q + a);
                    String qs = String.valueOf(quo);
                    if (qs.length() == 3 && qs.charAt(1) == '0') traps.add("mid0");
                    if (qs.endsWith("0")) traps.add("tail0");
                    if (firstDigit(d) < b) traps.add("short");
                }
                assertTrue(quo >= 10, "商须两位以上: " + q + a);
                boolean hard = rem || String.valueOf(quo).contains("0") || firstDigit(d) < b;
                assertTrue(hard, "提高档除法须含 商0/首位不够除/有余数 之一: " + q + a);
            }
        }
        assertTrue(traps.containsAll(Set.of("mid0", "tail0", "short", "rem")),
            "提高档四种错点形态都要覆盖，实测 " + traps);
    }

    @Test
    void verticalMul2d2dBasicBreaksDigitPattern() {
        Set<Character> digits = new LinkedHashSet<>();
        for (int i = 0; i < 200; i++) {
            for (Map<String, Object> it : items("mul2d2d", "basic", 4, "m22" + i)) {
                String q = (String) it.get("q");
                String body = q.substring(0, q.length() - 1);
                int m = body.indexOf(MUL);
                int a = Integer.parseInt(body.substring(0, m));
                int b = Integer.parseInt(body.substring(m + 1));
                assertEquals(a * b, Integer.parseInt((String) it.get("a")), "积不符: " + q);
                assertTrue(a >= 11 && a <= 99 && b >= 11 && b <= 99, "须两位数×两位数: " + q);
                assertTrue(a % 10 != 0 && b % 10 != 0, "个位 0 = 整十乘法（口算级）: " + q);
                assertNotEquals(a, b, "两因数相同 = 套路（23×23）: " + q);
                int carries = 0;
                for (int x : new int[]{(a / 10) * (b / 10), (a / 10) * (b % 10),
                                       (a % 10) * (b / 10), (a % 10) * (b % 10)}) {
                    if (x >= 10) carries++;
                }
                assertTrue(carries <= 1, "基础档最多一次进位: " + q);
                for (char c : (body.substring(0, m) + body.substring(m + 1)).toCharArray()) digits.add(c);
            }
        }
        assertTrue(digits.size() >= 7,
            "数字池须破 1/2/3 套路（审计：32×22/22×23/23×11/11×12/23×23），实测 " + digits);
    }

    // ───────────────── ④ 学段红线（本产品全类型） ─────────────────

    @Test
    void redLineHoldsForEveryPunchType() {
        for (String type : PUNCH_TYPES) {
            for (String level : List.of("basic", "advanced")) {
                for (int i = 0; i < 40; i++) {
                    for (Map<String, Object> it : items(type, level, 4, type + level + i)) {
                        String q = (String) it.get("q");
                        String a = (String) it.get("a");
                        assertTrue(q.endsWith(EQ), "题面须以＝结尾: " + q);
                        assertMatchesDecimalRedLine(q, type);
                        assertMatchesDecimalRedLine(a, type);
                        if (q.indexOf('.') >= 0) {
                            parseDec1(q, a, level);              // 小数题：一位小数 + 答案重算
                        } else if (a.contains(REM)) {
                            int[] db = parseDivQ(q);
                            int j = a.indexOf(REM);
                            int quo = Integer.parseInt(a.substring(0, j));
                            int r = Integer.parseInt(a.substring(j + REM.length()));
                            assertTrue(db[1] >= 2 && db[1] <= 9, "🔴 除数只能一位数: " + q);
                            assertTrue(r >= 1 && r < db[1], "余数越界: " + q + a);
                            assertEquals(db[0], db[1] * quo + r, "余数式不成立: " + q + a);
                        } else {
                            Expr e = new Expr(q);
                            int v = e.eval();                    // 内含"除法必整除"断言
                            assertEquals(Integer.parseInt(a), v, "答案与题面不符: " + q + a);
                            assertTrue(v >= 0, "结果不得为负: " + q + a);
                            for (int d : e.divisors) {
                                assertTrue(d >= 2 && d <= 9, "🔴 除数只能一位数: " + q + " -> " + d);
                            }
                        }
                    }
                }
            }
        }
    }

    /** 小数红线：出现的小数字面量必须恰一位小数。 */
    private void assertMatchesDecimalRedLine(String s, String type) {
        Matcher m = DECIMAL.matcher(s);
        while (m.find()) {
            String frac = m.group().substring(m.group().indexOf('.') + 1);
            assertEquals(1, frac.length(), "🔴 只允许一位小数（" + type + "）: " + s);
        }
    }

    // ───────────────── ⑤ 同质闸 ─────────────────

    @Test
    void oralGroupIsNotStructurallyHomogeneous() {
        // 提高档两位数×整十：审计里一行四道 31×80/58×80/33×80/81×30 —— 同结构须 ≤60%（4 道 → ≤2）
        for (int i = 0; i < 100; i++) {
            List<Map<String, Object>> its = items("mul2dtens", "advanced", 4, "t2t" + i);
            int tensFirst = 0;
            for (Map<String, Object> it : its) {
                String body = ((String) it.get("q"));
                int m = body.indexOf(MUL);
                if (Integer.parseInt(body.substring(0, m)) % 10 == 0) tensFirst++;
            }
            assertTrue(tensFirst <= 2 && (4 - tensFirst) <= 2,
                "两位数×整十 同结构占比超 60%: " + its);
        }
    }

    // ───────────────── ⑥ 确定性 ─────────────────

    @Test
    void sameSeedYieldsSameItems() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fillRows", false);
        body.put("seed", "d7B");                                  // 字符串 seed → hashCode 通路
        List<Map<String, Object>> gs = new ArrayList<>();
        for (String t : List.of("dec1", "div1d", "mixops3")) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("type", t);
            g.put("count", 4);
            g.put("level", "advanced");
            gs.add(g);
        }
        body.put("groups", gs);
        assertEquals(svc.generateItems(body).toString(), svc.generateItems(body).toString(),
            "同 seed 必须同输出");
    }

    // ───────────────── 工具 ─────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(String type, String level, int count, Object seed) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("type", type);
        g.put("count", count);
        if (level != null) g.put("level", level);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groups", List.of(g));
        body.put("fillRows", false);
        body.put("seed", seed);
        Map<String, Object> r = svc.generateItems(body);
        List<Map<String, Object>> groups = (List<Map<String, Object>>) r.get("groups");
        assertEquals(1, groups.size());
        return (List<Map<String, Object>>) groups.get(0).get("items");
    }

    /** {@code d÷b＝} → {d, b}。 */
    private int[] parseDivQ(String q) {
        String body = q.substring(0, q.length() - 1);
        int i = body.indexOf(DIV);
        assertTrue(i > 0, "须是除法题: " + q);
        return new int[]{Integer.parseInt(body.substring(0, i)), Integer.parseInt(body.substring(i + 1))};
    }

    /** "3.5"→35，"12"→120（十分位单位）。 */
    private static int tenths(String num) {
        int dot = num.indexOf('.');
        if (dot < 0) return Integer.parseInt(num) * 10;
        return Integer.parseInt(num.substring(0, dot)) * 10 + (num.charAt(dot + 1) - '0');
    }

    private static int firstDigit(int n) {
        while (n >= 10) n /= 10;
        return n;
    }

    /** 与出题器同构：竖式除法"本位有余数往下带"的步数（0 = 每位都整除 = 口算级）。 */
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

    /**
     * 全角整数算式求值器（递归下降，支持括号与两级运算）：求值的同时记录
     * 每个二元运算结果 / 每个括号组的值 / 乘法因数对 / 除数，供琐碎闸与红线断言使用；
     * 每遇除法即断言整除。
     */
    private static final class Expr {
        private final String src;
        private int pos;
        final List<Integer> results = new ArrayList<>();
        final List<Integer> parens = new ArrayList<>();
        final List<int[]> muls = new ArrayList<>();
        final List<Integer> divisors = new ArrayList<>();

        Expr(String s) {
            this.src = s.replace(EQ, "").trim();
        }

        int eval() {
            int v = expr();
            assertEquals(src.length(), pos, "算式未解析完: " + src);
            return v;
        }

        int ops() {
            return results.size();
        }

        private int expr() {
            int v = term();
            while (pos < src.length() && (src.charAt(pos) == ADD || src.charAt(pos) == SUB)) {
                char op = src.charAt(pos++);
                int r = term();
                v = op == ADD ? v + r : v - r;
                results.add(v);
            }
            return v;
        }

        private int term() {
            int v = factor();
            while (pos < src.length() && (src.charAt(pos) == MUL || src.charAt(pos) == DIV)) {
                char op = src.charAt(pos++);
                int r = factor();
                if (op == MUL) {
                    muls.add(new int[]{v, r});
                    v = v * r;
                } else {
                    divisors.add(r);
                    assertTrue(r != 0 && v % r == 0, "🔴 除法必整除: " + src);
                    v = v / r;
                }
                results.add(v);
            }
            return v;
        }

        private int factor() {
            assertTrue(pos < src.length(), "算式意外结束: " + src);
            if (src.charAt(pos) == '(') {
                pos++;
                int v = expr();
                assertTrue(pos < src.length() && src.charAt(pos) == ')', "括号不匹配: " + src);
                pos++;
                parens.add(v);
                return v;
            }
            int st = pos;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            assertTrue(pos > st, "解析失败（期望数字）: " + src + " @" + pos);
            return Integer.parseInt(src.substring(st, pos));
        }
    }
}
