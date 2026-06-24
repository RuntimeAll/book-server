package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 「题目内容 → blockJson」确定性转换器入参（POST /teacher/format/to-block）。
 *
 * <p>AI 只产最小内容：自然 markdown 题干 + 选项内容数组；Java 确定性转换器
 * （{@link org.dromara.book.util.BlockJsonConverter}）拼出合法 blockJson
 * （{@code {v:1,rows:[{cells:[block]}]}}）。这是录入 + 将来举一反三共用的底座，
 * 不依赖 DB、对外永不抛（识别不了的部分降级成 markdown 文本块）。
 *
 * @author backend-dev
 */
@Data
public class FormatToBlockBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题型：1 选择 / 2 填空 / 3 判断 / 4 计算 / 5 解答 / 6 作图。
     * 仅 type=1（选择）或 options 非空时才处理选项块。
     */
    private Integer type;

    /**
     * 题干 markdown（自然写法）：可含小问 {@code （1）（2）}、图标记
     * {@code ![](url)}（可带 {@code {w=45}} 宽度后缀）、表格、{@code $LaTeX$}。
     */
    private String stem;

    /**
     * 选择题选项内容数组（label 由下标自动 A/B/C/D…）。非选择题为空/null。
     * 每项本身可含 {@code ![](url)} 小图，会被进一步抽图。
     */
    private List<String> options;
}
