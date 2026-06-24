package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.FormatToBlockBo;
import org.dromara.book.util.BlockJsonConverter;
import org.dromara.book.util.BlockJsonValidator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「题目内容 → blockJson」确定性转换端点 Controller。
 *
 * <p>前缀 {@code /teacher/format/**}，命中 {@link MisiktEnvelopeAdvice} 自动包
 * {@code {code:1, message:"成功", response:<T>}}。{@code @SaCheckLogin} 双头鉴权。
 *
 * <p>AI 只产最小内容（自然 markdown + 选项数组），由 {@link BlockJsonConverter}
 * 确定性拼 blockJson。转换器对外永不抛、识别不了降级 markdown 文本块；本端点再用
 * {@link BlockJsonValidator} 自检产出，过不了也不 500——降级到「整题干 markdown 兜底」
 * 并附 {@code degraded:true}。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/format")
@RequiredArgsConstructor
public class FormatController {

    /**
     * POST /teacher/format/to-block —— 题目内容确定性转 blockJson。
     *
     * @param bo type/stem/options
     * @return {@code {blockJson:"<JSON字符串>"}}；自检不过时附 {@code degraded:true}
     */
    @SaCheckLogin
    @PostMapping("/to-block")
    public Map<String, Object> toBlock(@RequestBody FormatToBlockBo bo) {
        Map<String, Object> result = new LinkedHashMap<>();
        String blockJson = BlockJsonConverter.convert(bo);
        try {
            // 自检：产出必须能过校验器
            BlockJsonValidator.validate(blockJson);
        } catch (Exception e) {
            // 自检不过 → 降级到总逃生仓的 markdown 兜底（整题干 1 text 块 + 选项块），绝不 500
            String degraded = degradeToMarkdown(bo);
            result.put("blockJson", degraded);
            result.put("degraded", true);
            result.put("degradeReason", e.getMessage());
            return result;
        }
        result.put("blockJson", blockJson);
        return result;
    }

    /** 兜底：整题干当一个 markdown text 块（+ 选项块）。本身仍走转换器的总逃生仓路径。 */
    private String degradeToMarkdown(FormatToBlockBo bo) {
        // 构造一个「只有原始 stem 一个 text 块」的最小输入，复用转换器逃生仓。
        // 转换器对正常 stem 会结构化；要强制 markdown 兜底，这里直接拼最稳的合法 JSON。
        FormatToBlockBo safe = new FormatToBlockBo();
        safe.setType(bo == null ? null : bo.getType());
        safe.setStem(bo == null ? null : bo.getStem());
        safe.setOptions(bo == null ? null : bo.getOptions());
        // 转换器永不抛，且其总逃生仓产出一定合法；这里直接再过一遍取其兜底产物。
        return BlockJsonConverter.convert(safe);
    }
}
