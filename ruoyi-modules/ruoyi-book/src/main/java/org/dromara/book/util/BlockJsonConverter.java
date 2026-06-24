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

    // ===== stem 处理：抽图 → 切段 → 每段切小问 =====

    private static void appendStemRows(ObjectMapper om, ArrayNode rows, String stem) {
        // 2. 抽图：把 stem 按图位切成 [文本段, 图, 文本段, …]
        List<Segment> segments = splitByImages(stem);
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

    private static void appendOptionRows(ObjectMapper om, ArrayNode rows, List<String> options) {
        for (int i = 0; i < options.size(); i++) {
            String content = options.get(i);
            String label = labelOf(i);
            addRow(rows, optionBlock(om, label, content));
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
