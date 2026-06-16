package org.dromara.book.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;

import java.util.Set;

/**
 * 题目结构化网格块（block）JSON 校验器（PRD-A-015 §10.1 锁定 schema）。
 *
 * <p>整题一份 JSON：{@code { "v":1, "rows":[ { "cells":[ <block> ] } ] }}。
 * block 三型（type 判别）：
 * <ul>
 *   <li>text   {@code { type:"text", md:string }}</li>
 *   <li>image  {@code { type:"image", url:string, width:int 1-100, align:"left"|"center"|"right" }}</li>
 *   <li>option {@code { type:"option", label:string, content:[ text|image block ] }}</li>
 * </ul>
 *
 * <p>校验失败抛 {@link ServiceException}（带中文消息，定位到行/格），命中后被全局异常处理器
 * 转成非 {@code code:1}（/teacher/** 走 MisiktEnvelopeAdvice）。校验通过返回解析后的根 {@link JsonNode}。
 *
 * @author backend-dev (PRD-A-015)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockJsonValidator {

    /** option.content 仅允许 text / image 两型（不允许嵌套 option） */
    private static final Set<String> LEAF_BLOCK_TYPES = Set.of("text", "image");
    /** image.align 合法取值 */
    private static final Set<String> IMAGE_ALIGNS = Set.of("left", "center", "right");

    /**
     * 校验 block JSON 字符串。合法返回解析后的根 JsonNode；非法抛 ServiceException。
     *
     * @param blockJson block 文档 JSON 原文（不可空白，调用方应已 @NotBlank 兜底）
     * @return 解析后的根 JsonNode
     */
    public static JsonNode validate(String blockJson) {
        if (StringUtils.isBlank(blockJson)) {
            throw new ServiceException("题目结构数据非法：blockJson 不能为空");
        }
        JsonNode root;
        try {
            root = JsonUtils.getObjectMapper().readTree(blockJson);
        } catch (Exception e) {
            throw new ServiceException("题目结构数据非法：不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new ServiceException("题目结构数据非法：根必须是 JSON 对象");
        }

        // 根：必须有 v(数字) + rows(数组)
        JsonNode vNode = root.get("v");
        if (vNode == null || !vNode.isNumber()) {
            throw new ServiceException("题目结构数据非法：缺少版本号 v 或 v 不是数字");
        }
        JsonNode rowsNode = root.get("rows");
        if (rowsNode == null || !rowsNode.isArray()) {
            throw new ServiceException("题目结构数据非法：rows 必须是数组");
        }

        for (int r = 0; r < rowsNode.size(); r++) {
            JsonNode row = rowsNode.get(r);
            int rowNo = r + 1;
            if (row == null || !row.isObject()) {
                throw new ServiceException("题目结构数据非法：第" + rowNo + "行不是对象");
            }
            JsonNode cells = row.get("cells");
            if (cells == null || !cells.isArray()) {
                throw new ServiceException("题目结构数据非法：第" + rowNo + "行 cells 必须是数组");
            }
            for (int c = 0; c < cells.size(); c++) {
                int cellNo = c + 1;
                validateBlock(cells.get(c), "第" + rowNo + "行第" + cellNo + "格", false);
            }
        }
        return root;
    }

    /**
     * 校验单个 block。
     *
     * @param block    待校验节点
     * @param locate   定位前缀（如 "第1行第2格"），用于拼错误消息
     * @param leafOnly true = 处于 option.content 内，仅允许 text/image（不允许 option 嵌套）
     */
    private static void validateBlock(JsonNode block, String locate, boolean leafOnly) {
        if (block == null || !block.isObject()) {
            throw new ServiceException("题目结构数据非法：" + locate + " 不是对象");
        }
        JsonNode typeNode = block.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new ServiceException("题目结构数据非法：" + locate + " 缺少 type");
        }
        String type = typeNode.asText();

        switch (type) {
            case "text" -> {
                JsonNode md = block.get("md");
                if (md == null || !md.isTextual()) {
                    throw new ServiceException("题目结构数据非法：" + locate + " text 块 md 必须是字符串");
                }
            }
            case "image" -> validateImage(block, locate);
            case "option" -> {
                if (leafOnly) {
                    throw new ServiceException("题目结构数据非法：" + locate + " option 块不能嵌套在选项内容中");
                }
                validateOption(block, locate);
            }
            default -> throw new ServiceException("题目结构数据非法：" + locate + " 未知 type=" + type);
        }
    }

    private static void validateImage(JsonNode block, String locate) {
        JsonNode url = block.get("url");
        if (url == null || !url.isTextual()) {
            throw new ServiceException("题目结构数据非法：" + locate + " image 块 url 必须是字符串");
        }
        JsonNode width = block.get("width");
        if (width == null || !width.isIntegralNumber()) {
            throw new ServiceException("题目结构数据非法：" + locate + " image 块 width 必须是整数");
        }
        int w = width.asInt();
        if (w < 1 || w > 100) {
            throw new ServiceException("题目结构数据非法：" + locate + " image 块 width 须在 1-100 之间");
        }
        JsonNode align = block.get("align");
        if (align == null || !align.isTextual() || !IMAGE_ALIGNS.contains(align.asText())) {
            throw new ServiceException("题目结构数据非法：" + locate + " image 块 align 须为 left/center/right");
        }
    }

    private static void validateOption(JsonNode block, String locate) {
        JsonNode label = block.get("label");
        if (label == null || !label.isTextual()) {
            throw new ServiceException("题目结构数据非法：" + locate + " option 块 label 必须是字符串");
        }
        JsonNode content = block.get("content");
        if (content == null || !content.isArray()) {
            throw new ServiceException("题目结构数据非法：" + locate + " option 块 content 必须是数组");
        }
        for (int i = 0; i < content.size(); i++) {
            JsonNode child = content.get(i);
            String childLocate = locate + " 选项内容第" + (i + 1) + "项";
            // leafOnly=true：option.content 仅允许 text|image
            validateBlock(child, childLocate, true);
            // 二次明确拦截（validateBlock 内已对 option 做 leafOnly 校验，这里冗余防御非允许类型）
            String childType = child.get("type") == null ? "" : child.get("type").asText();
            if (!LEAF_BLOCK_TYPES.contains(childType)) {
                throw new ServiceException("题目结构数据非法：" + childLocate + " 只能是 text 或 image 块");
            }
        }
    }
}
