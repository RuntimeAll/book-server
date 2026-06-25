package org.dromara.book.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.book.domain.bo.FormatToBlockBo;
import org.dromara.common.json.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「题目内容 → blockJson」确定性转换器（纯函数，不依赖 DB）。
 *
 * <p>把 AI 产出的最小内容（自然 markdown 题干 + 选项内容数组）确定性地拼成合法
 * blockJson（{@code {v:1,rows:[{cells:[block]}]}}），产出保证能过
 * {@link BlockJsonValidator}。录入 + 将来举一反三都调它。
 *
 * <p>🔴 <b>最高优先级铁律 = 逃生仓（降级路径）</b>：转换器<b>对外永不抛</b>。
 * 任何识别不了的部分降级成 markdown 文本块；任何步骤抛异常都被 catch 住，
 * 最坏回退到「整道题干当一个 markdown text 块」+ 选项块。识别得了就结构化，
 * 识别不了就 markdown 兜底。
 *
 * <p>转换规则（每步带逃生仓）：
 * <ol>
 *   <li>stem 空 → {@code {v:1,rows:[]}}。</li>
 *   <li>抽图：扫 {@code ![alt](url)}（可带 {@code {w=NN}} 宽度后缀），按图位切段；
 *       图 → image 块（width 缺省 45，align 缺省 center；语法残缺 → 当普通文本）。</li>
 *   <li>每个文本段：按行首 {@code （n）/(n)} 切小问，引导句 + 每小问各 1 text 块；
 *       切不到小问 → 整段 1 text 块（逃生）。</li>
 *   <li>选项（type=1 或 options 非空）：每项 → option 块，label 按下标 A/B/C/…，
 *       content 再走「抽图 + 文本」。</li>
 *   <li>每个块独占一 row 一 cell。</li>
 * </ol>
 *
 * @author backend-dev
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockJsonConverter {

    /** markdown 图：![alt](url) ，url 后可选 {w=NN} 宽度后缀（在 alt 里或括号外都尽量容忍）。 */
    private static final Pattern IMG_PATTERN =
        Pattern.compile("!\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+[^)]*)?\\)(?:\\s*\\{w=(\\d{1,3})})?");

    /** 行首小问号：（1） / (1) / （12） 等（全角/半角括号 + 数字）。 */
    private static final Pattern SUBQ_LINE_PATTERN =
        Pattern.compile("(?m)^\\s*[（(](\\d{1,3})[）)]");

    /**
     * 纯富文本（答案/解析）结构标记：在标记**前**插断行，让连续段落能按逻辑切块。
     * 零宽前瞻匹配 → replaceAll 把标记起点替成换行（标记本身留在新段开头）：
     * 选项分析（A选项: / A 选项：）｜小问（（1）/(1)）｜步骤（①②③ / 第一步）。
     * 🔴 仅服务于「拆行更好读」，切错最多多/少一行，转换器整体仍有逃生仓不抛。
     */
    private static final Pattern RICH_BREAK_PATTERN =
        Pattern.compile(
            "(?=[ABCDEFGＡＢＣＤ]\\s*选\\s*项\\s*[：:])"
          + "|(?=[（(]\\s*\\d{1,2}\\s*[)）])"
          + "|(?=[①②③④⑤⑥⑦⑧⑨⑩⑪⑫])"
          + "|(?=第\\s*[一二三四五六七八九十]+\\s*步)");

    /** LaTeX 命令（\frac \sqrt \pm …）：可视长度估算时整体抹掉命令名，不计入字符数。 */
    private static final Pattern LATEX_CMD_PATTERN =
        Pattern.compile("\\\\[a-zA-Z]+");

    private static final int DEFAULT_IMG_WIDTH = 45;
    private static final String DEFAULT_IMG_ALIGN = "center";
    private static final String OPTION_LABELS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * 转换入口。<b>对外永不抛</b>，永远返回合法 blockJson 字符串。
     *
     * @param bo 入参（type/stem/options）
     * @return 合法 blockJson（{@code {v:1,rows:[...]}}）
     */
    public static String convert(FormatToBlockBo bo) {
        ObjectMapper om = JsonUtils.getObjectMapper();
        String stem = bo == null ? null : bo.getStem();
        List<String> options = bo == null ? null : bo.getOptions();
        Integer type = bo == null ? null : bo.getType();

        try {
            ObjectNode root = om.createObjectNode();
            root.put("v", 1);
            ArrayNode rows = root.putArray("rows");

            // 1. stem 空 → 仅可能有选项；rows 可为空
            if (stem != null && !stem.isBlank()) {
                appendStemRows(om, rows, stem);
            }

            // 4. 选项：type=1 选择 或 options 非空 都处理
            if (shouldHandleOptions(type, options)) {
                appendOptionRows(om, rows, options);
            }

            return om.writeValueAsString(root);
        } catch (Exception e) {
            // 6. 总逃生仓：任何失败 → 整题干 markdown text 块 + 选项块兜底
            return fallback(om, stem, type, options);
        }
    }

    /**
     * 纯富文本（答案/解析/将来变式解析）→ blockJson。<b>对外永不抛</b>。
     *
     * <p>与 {@link #convert} 共用 image/text 块原语，但<b>无 options 概念</b>，且额外按
     * {@link #RICH_BREAK_PATTERN}（选项分析/小问/步骤标记）先插断行再切块——解析常是「无换行
     * 连续段」，靠这步把 A选项/B选项/（1）/（2）/①② 拆成各自一块，渲染不再糊成一坨。
     *
     * @param md 富文本 markdown（如 analyzeText）
     * @return 合法 blockJson（{@code {v:1,rows:[...]}}），识别不了整段兜成一个 text 块
     */
    public static String convertRichText(String md) {
        ObjectMapper om = JsonUtils.getObjectMapper();
        try {
            ObjectNode root = om.createObjectNode();
            root.put("v", 1);
            ArrayNode rows = root.putArray("rows");
            if (md != null && !md.isBlank()) {
                // 1. 结构标记前插断行（零宽前瞻 → 标记留新段首）
                String normalized = RICH_BREAK_PATTERN.matcher(md).replaceAll("\n\n");
                // 2. 按图位切段，文本段再按空行切成多块
                for (Segment seg : splitByImages(normalized)) {
                    if (seg.isImage) {
                        addRow(rows, imageBlock(om, seg.url, seg.width));
                    } else if (seg.text != null) {
                        for (String part : seg.text.split("\\n{2,}")) {
                            String p = part.strip();
                            if (!p.isEmpty()) {
                                addRow(rows, textBlock(om, p));
                            }
                        }
                    }
                }
            }
            return om.writeValueAsString(root);
        } catch (Exception e) {
            // 逃生仓：整段当一个 markdown text 块
            return fallback(om, md, null, null);
        }
    }

    // ===== stem 处理：抽图 → 切段 → 每段切小问 =====

    /** 行内小问号（不限行首）：用于「内联 (1)…(2)…」也切段。 */
    private static final Pattern SUBQ_INLINE_PATTERN =
        Pattern.compile("[（(]\\s*\\d{1,2}\\s*[)）]");

    /**
     * 「引用连接字」：(N) 前紧跟这些字时，该 (N) 是对前文小问的句中引用、不是新小问起点。
     * 例：「在（2）的条件下」「由（1）得」「将（3）代入」。守卫用，命中则不切。
     */
    private static final String REF_LEAD_CHARS = "在的与和对由把将这那从据如按用借参照依结合根";

    /**
     * 判断 stem 中位于 {@code idx} 的 {@code (N)} 是否为「真小问起点」（而非句中引用）。
     *
     * <p>🔴 G8 守卫：题干里既有真小问标记 (1)(2)(3)…，也有对前文小问的引用
     * （「（3）在（2）的条件下…」「（4）在（1）（2）（3）的条件下…」）。早先的内联注入对
     * <b>任意位置</b>的 (N) 都断行 → 把句子切碎、引用切成孤儿段。此守卫只在真起点放行。
     *
     * <p>判据（满足其一 = 真起点，注入断行）：
     * <ul>
     *   <li>(N) 前是串首 / 换行（紧跟行首）；或</li>
     *   <li>(N) 前是分隔/引导标点（{@code ；;。：:？?！!}）或 ≥2 个空格
     *       （上一小问已结束，或「计算：(1)…」这类引导冒号后的首小问）。</li>
     * </ul>
     * 反之（满足其一 = 句中引用，不切）：
     * <ul>
     *   <li>(N) 前紧跟连接字（在/的/与/和/对/由/把/将/这/那…，见 {@link #REF_LEAD_CHARS}）；或</li>
     *   <li>(N) 前紧跟另一个 {@code )/）}（如「（1）（2）（3）」连排引用）。</li>
     * </ul>
     * 其余暧昧情况（前面是普通汉字/标点，既非分隔也非连接字）→ 宁可<b>不切</b>（保守，
     * 宁漏拆个别真小问也别把句子切碎，符合「谨慎正则」要求）。
     */
    private static boolean isRealSubQStart(String stem, int idx) {
        // 跳过 (N) 前的空白，定位「前一个非空白字符」
        int j = idx - 1;
        int spaceRun = 0;
        while (j >= 0 && Character.isWhitespace(stem.charAt(j))) {
            // 换行 = 行首起点，直接判真
            if (stem.charAt(j) == '\n' || stem.charAt(j) == '\r') {
                return true;
            }
            spaceRun++;
            j--;
        }
        // 串首（前面只有空白或全空）→ 真起点
        if (j < 0) {
            return true;
        }
        char prev = stem.charAt(j);
        // 引用：前紧跟连接字 → 不切
        if (REF_LEAD_CHARS.indexOf(prev) >= 0) {
            return false;
        }
        // 引用：前紧跟另一个右括号（连排引用「（1）（2）」）→ 不切
        if (prev == ')' || prev == '）') {
            return false;
        }
        // 分隔/引导标点（；;。：:？?！!）→ 上一小问结束 或 引导冒号（「计算：(1)…」）→ 真起点
        if (prev == '；' || prev == ';' || prev == '。'
            || prev == '：' || prev == ':'
            || prev == '？' || prev == '?' || prev == '！' || prev == '!') {
            return true;
        }
        // 紧贴前文且只隔 ≥2 个空格 → 视作分隔 → 真起点
        if (spaceRun >= 2) {
            return true;
        }
        // 其余暧昧（前面是普通汉字/单空格/其它标点）→ 保守不切
        return false;
    }

    private static void appendStemRows(ObjectMapper om, ArrayNode rows, String stem) {
        // 🔴 PRD-C-204：题干含 ≥2 个小问标记但写在「同一行内联」(如「计算：(1)…；(2)…」)时，
        // 行首切法(SUBQ_LINE)切不到 → 先在每个 (n) 前注入断行，让其也拆成各自一块。
        // 🔴 G8 守卫：只在「真小问起点」处注入断行，句中引用「在（2）的…」「（1）（2）（3）」不切
        // （见 isRealSubQStart）。守卫 ≥2 真起点：避免把单个引用误当小问；普通题干不动。
        Matcher mc = SUBQ_INLINE_PATTERN.matcher(stem);
        List<Integer> realStarts = new ArrayList<>();
        while (mc.find()) {
            if (isRealSubQStart(stem, mc.start())) {
                realStarts.add(mc.start());
            }
        }
        String s = stem;
        if (realStarts.size() >= 2) {
            // 从后往前在每个「真起点」前插断行（倒序避免位移失配）；引用位置一概不动。
            StringBuilder sb = new StringBuilder(stem);
            for (int k = realStarts.size() - 1; k >= 0; k--) {
                sb.insert(realStarts.get(k), "\n\n");
            }
            s = sb.toString();
        }
        // 2. 抽图：把 stem 按图位切成 [文本段, 图, 文本段, …]
        List<Segment> segments = splitByImages(s);
        for (Segment seg : segments) {
            if (seg.isImage) {
                addRow(rows, imageBlock(om, seg.url, seg.width));
            } else {
                appendTextSegment(om, rows, seg.text);
            }
        }
    }

    /** 把一段文本按行首小问切：引导句 1 块 + 每小问 1 块；切不到 → 整段 1 块（逃生）。 */
    private static void appendTextSegment(ObjectMapper om, ArrayNode rows, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher m = SUBQ_LINE_PATTERN.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        // 3. 切不到小问 → 整段当 1 text 块（逃生：markdown 原样）
        if (starts.isEmpty()) {
            addRow(rows, textBlock(om, text.strip()));
            return;
        }
        // 引导句（首个小问前的文本），非空才出块
        String lead = text.substring(0, starts.get(0)).strip();
        if (!lead.isEmpty()) {
            addRow(rows, textBlock(om, lead));
        }
        // 每个小问 1 块
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            String sub = text.substring(from, to).strip();
            if (!sub.isEmpty()) {
                addRow(rows, textBlock(om, sub));
            }
        }
    }

    /**
     * 按 markdown 图位把文本切成有序段。图语法残缺（如缺 url）走兜底当普通文本（不进图分支）。
     */
    private static List<Segment> splitByImages(String text) {
        List<Segment> segs = new ArrayList<>();
        Matcher m = IMG_PATTERN.matcher(text);
        int last = 0;
        while (m.find()) {
            String url = m.group(1);
            // 图语法残缺（url 空）→ 不切，留给文本兜底
            if (url == null || url.isBlank()) {
                continue;
            }
            if (m.start() > last) {
                segs.add(Segment.text(text.substring(last, m.start())));
            }
            Integer width = parseWidth(m.group(2));
            segs.add(Segment.image(url.strip(), width));
            last = m.end();
        }
        if (last < text.length()) {
            segs.add(Segment.text(text.substring(last)));
        }
        // 全程无图 → 整段一个文本段
        if (segs.isEmpty()) {
            segs.add(Segment.text(text));
        }
        return segs;
    }

    private static Integer parseWidth(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int w = Integer.parseInt(raw.trim());
            // 解析不了 / 越界用缺省（逃生）
            if (w < 1 || w > 100) {
                return null;
            }
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 选项处理 =====

    private static boolean shouldHandleOptions(Integer type, List<String> options) {
        if (options == null || options.isEmpty()) {
            return false;
        }
        // type=1 选择 一定处理；非选择但 options 非空也处理（宽容）
        return true;
    }

    /**
     * 选项 → option 块，按可视长度智能分组布局：
     * <ul>
     *   <li>短选项横排（1 行 N cell），中等 2×2 网格，长选项竖排（各自一行）。</li>
     *   <li>任一选项含图 → N=4 走 2×2，否则各自一行（图选项偏高，别强行横排）。</li>
     * </ul>
     * 🔴 逃生仓：长度/分组任何异常 → 退回「各选项各自一行」（原竖排行为），绝不抛。
     */
    private static void appendOptionRows(ObjectMapper om, ArrayNode rows, List<String> options) {
        // 先把每个选项构造成 option 块（构造本身已带逃生仓）
        List<ObjectNode> optBlocks = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            optBlocks.add(optionBlock(om, labelOf(i), options.get(i)));
        }

        List<Integer> groups;
        try {
            boolean hasImage = options.stream().anyMatch(BlockJsonConverter::optionHasImage);
            int maxLen = 0;
            for (String opt : options) {
                maxLen = Math.max(maxLen, visualLen(opt));
            }
            groups = decideLayout(options.size(), maxLen, hasImage);
        } catch (Exception e) {
            // 逃生仓：分组算崩 → 各自一行
            groups = null;
        }
        if (groups == null) {
            for (ObjectNode opt : optBlocks) {
                addRow(rows, opt);
            }
            return;
        }

        // 按分组把 option 块铺成行（每组 = 一行的 cell 数）
        int idx = 0;
        for (int cnt : groups) {
            List<ObjectNode> rowCells = new ArrayList<>(cnt);
            for (int k = 0; k < cnt && idx < optBlocks.size(); k++) {
                rowCells.add(optBlocks.get(idx++));
            }
            addMultiCellRow(rows, rowCells);
        }
        // 防御：分组没覆盖到的尾部选项（理论不该发生）兜底各自一行
        while (idx < optBlocks.size()) {
            addRow(rows, optBlocks.get(idx++));
        }
    }

    /**
     * 布局决策：返回每行的选项数列表（如 [4]=横排 / [2,2]=2×2 / [1,1,1,1]=竖排）。
     *
     * @param n        选项数
     * @param maxLen   最长选项可视长度
     * @param hasImage 是否有选项含图
     * @return 每行 cell 数的列表
     */
    private static List<Integer> decideLayout(int n, int maxLen, boolean hasImage) {
        if (hasImage) {
            // 图选项：仅 N=4 走 2×2，其余各自一行（图偏高，不强行横排）
            return (n == 4) ? List.of(2, 2) : verticalGroups(n);
        }
        if (n == 4) {
            if (maxLen <= 5) {
                return List.of(4);            // 很短（纯数字/单符号，如 2003）→ 1 行 4 cell 横排
            }
            if (maxLen <= 20) {
                return List.of(2, 2);         // 中等（含简短公式 ±√9=±3）→ 2×2 网格
            }
            return verticalGroups(4);         // 长选项 → 竖排
        }
        if (n == 2 || n == 3) {
            if (maxLen <= 12) {
                return List.of(n);            // 短 → 1 行 N cell 横排
            }
            return verticalGroups(n);         // 否则竖排
        }
        // N≥5 或其他 → 竖排兜底
        return verticalGroups(n);
    }

    /** 竖排分组：n 个各占一行（[1,1,…]）。 */
    private static List<Integer> verticalGroups(int n) {
        List<Integer> g = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            g.add(1);
        }
        return g;
    }

    /**
     * 估算选项可视长度：去掉 {@code $ \ { }} 和常见 LaTeX 命令后的字符数（作长度代理）。
     * 任何异常 → 退回原始长度（不影响逃生仓，仅长度估算的局部兜底）。
     */
    private static int visualLen(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            String s = raw;
            // LaTeX 命令（\frac \sqrt \pm 等）→ 折成 1 个占位字符：命令渲染后约占 1 个符号宽，
            // 既不按原文长度高估、也不删成 0 低估（删 0 会把 $\pm\sqrt{9}=\pm3$ 误判成超短选项）。
            s = LATEX_CMD_PATTERN.matcher(s).replaceAll("#");
            // 去结构性符号 $ \ { }（剩余裸反斜杠/花括号不计入可视宽度）
            s = s.replaceAll("[$\\\\{}]", "");
            // 折叠连续空白为单字符再去首尾
            s = s.replaceAll("\\s+", " ").strip();
            return s.length();
        } catch (Exception e) {
            return raw.length();
        }
    }

    /** 选项内容是否含图（markdown 图语法）。 */
    private static boolean optionHasImage(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        try {
            Matcher m = IMG_PATTERN.matcher(content);
            while (m.find()) {
                String url = m.group(1);
                if (url != null && !url.isBlank()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String labelOf(int idx) {
        if (idx >= 0 && idx < OPTION_LABELS.length()) {
            return String.valueOf(OPTION_LABELS.charAt(idx));
        }
        // 超过 26 个选项（极端）→ 用数字兜底，仍是合法字符串 label
        return String.valueOf(idx + 1);
    }

    // ===== block 构造（option.content 仅 text/image，符合 validator leafOnly）=====

    private static ObjectNode optionBlock(ObjectMapper om, String label, String content) {
        ObjectNode opt = om.createObjectNode();
        opt.put("type", "option");
        opt.put("label", label);
        ArrayNode arr = opt.putArray("content");
        if (content == null || content.isBlank()) {
            // 空选项也要合法：放一个空 text 块
            arr.add(textBlock(om, ""));
            return opt;
        }
        // 选项内容再走「抽图 + 文本」；识别不了就纯 text 块（逃生）
        try {
            List<Segment> segs = splitByImages(content);
            for (Segment seg : segs) {
                if (seg.isImage) {
                    arr.add(imageBlock(om, seg.url, seg.width));
                } else if (seg.text != null && !seg.text.isBlank()) {
                    arr.add(textBlock(om, seg.text.strip()));
                }
            }
            if (arr.isEmpty()) {
                arr.add(textBlock(om, content.strip()));
            }
        } catch (Exception e) {
            arr.removeAll();
            arr.add(textBlock(om, content.strip()));
        }
        return opt;
    }

    private static ObjectNode textBlock(ObjectMapper om, String md) {
        ObjectNode b = om.createObjectNode();
        b.put("type", "text");
        b.put("md", md == null ? "" : md);
        return b;
    }

    private static ObjectNode imageBlock(ObjectMapper om, String url, Integer width) {
        ObjectNode b = om.createObjectNode();
        b.put("type", "image");
        b.put("url", url);
        b.put("width", (width == null) ? DEFAULT_IMG_WIDTH : width);
        b.put("align", DEFAULT_IMG_ALIGN);
        return b;
    }

    /** 把单个 block 包成一 row 一 cell。 */
    private static void addRow(ArrayNode rows, ObjectNode block) {
        ObjectNode row = rows.objectNode();
        ArrayNode cells = row.putArray("cells");
        cells.add(block);
        rows.add(row);
    }

    /** 把多个 block 并排放进一 row（前端按 repeat(N,1fr) 渲染成 N 列）。空列表不出行。 */
    private static void addMultiCellRow(ArrayNode rows, List<ObjectNode> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        ObjectNode row = rows.objectNode();
        ArrayNode cells = row.putArray("cells");
        for (ObjectNode b : blocks) {
            cells.add(b);
        }
        rows.add(row);
    }

    // ===== 总逃生仓 =====

    /** 最坏回退：整道题干当一个 markdown text 块 + 选项块。本方法自身再兜一层 try。 */
    private static String fallback(ObjectMapper om, String stem, Integer type, List<String> options) {
        try {
            ObjectNode root = om.createObjectNode();
            root.put("v", 1);
            ArrayNode rows = root.putArray("rows");
            if (stem != null && !stem.isBlank()) {
                addRow(rows, textBlock(om, stem));
            }
            if (options != null && !options.isEmpty()) {
                for (int i = 0; i < options.size(); i++) {
                    String c = options.get(i);
                    ObjectNode opt = om.createObjectNode();
                    opt.put("type", "option");
                    opt.put("label", labelOf(i));
                    ArrayNode arr = opt.putArray("content");
                    arr.add(textBlock(om, c == null ? "" : c));
                    addRow(rows, opt);
                }
            }
            return om.writeValueAsString(root);
        } catch (Exception e) {
            // 连兜底都失败：返回死活合法的空文档
            return "{\"v\":1,\"rows\":[]}";
        }
    }

    // ===== 内部段 =====

    private static final class Segment {
        final boolean isImage;
        final String text;
        final String url;
        final Integer width;

        private Segment(boolean isImage, String text, String url, Integer width) {
            this.isImage = isImage;
            this.text = text;
            this.url = url;
            this.width = width;
        }

        static Segment text(String t) {
            return new Segment(false, t, null, null);
        }

        static Segment image(String url, Integer width) {
            return new Segment(true, null, url, width);
        }
    }
}
