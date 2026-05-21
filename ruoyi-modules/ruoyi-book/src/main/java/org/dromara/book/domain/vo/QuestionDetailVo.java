package org.dromara.book.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * /teacher/question/select/{id} 详情 VO。
 *
 * <p>继承 {@link QuestionItemVo} 复用列表字段，新增详情专属字段：
 * <ul>
 *   <li>{@code answer} 答案文本</li>
 *   <li>{@code answerImg} 答案图 URL（DB 列 answer_img_url）</li>
 *   <li>{@code explain} 解析文本</li>
 *   <li>{@code explainImg} 解析图 URL（DB 列 explain_img_url）</li>
 *   <li>{@code fileBin} 笔迹数据 URL（DB 列 file_bin_url）</li>
 *   <li>{@code videoUrl} 视频讲解 URL</li>
 *   <li>{@code questionStdKnowledges} 标准库标注知识点（source='S'，仅详情返）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionDetailVo extends QuestionItemVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 答案文本（V0.1 实数据未必有 — DB 未单独列存，从 correctAnswer 衍生）
     */
    private String answer;

    /**
     * 答案图 URL（DB 列 answer_img_url）
     */
    private String answerImg;

    /**
     * 解析文本（V0.1 DB 无单列 — 暂返 null，跟 misikt 抓包 sample 一致）
     */
    private String explain;

    /**
     * 解析图 URL（DB 列 explain_img_url）
     */
    private String explainImg;

    /**
     * 笔迹数据 URL（DB 列 file_bin_url）
     */
    private String fileBin;

    /**
     * 视频讲解 URL（DB 列 video_url；misikt 自己保留 Url 后缀）
     */
    private String videoUrl;

    /**
     * 选项 JSON 字符串
     */
    private String optionsJson;

    /**
     * 评分标准 JSON 字符串
     */
    private String scoreStd;

    /**
     * 标准库标注知识点（source='S'，仅详情返）
     */
    private List<QuestionKnowledgeVo> questionStdKnowledges;
}
