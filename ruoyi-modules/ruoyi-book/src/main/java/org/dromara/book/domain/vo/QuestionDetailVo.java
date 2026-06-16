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
 *   <li>{@code answer} 答案文本（来源 biz_text_content content_type='A'，PRD-B-013 后唯一来源）</li>
 *   <li>{@code answerImg} 答案图 URL（DB 列 answer_img_url）</li>
 *   <li>{@code explain} 解析文本（来源 biz_text_content content_type='E'）</li>
 *   <li>{@code explainImg} 解析图 URL（DB 列 explain_img_url）</li>
 *   <li>{@code fileBin} 笔迹数据 URL（DB 列 file_bin_url）</li>
 *   <li>{@code questionStdKnowledges} 标准库标注知识点（source='S'，仅详情返）</li>
 * </ul>
 *
 * <p>🔴 PRD-B-013 减法：删除 3 个详情专属字段（详见 PRD-B-013 §scope.B 后端段）。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionDetailVo extends QuestionItemVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 答案文本（biz_text_content content_type='A' — PRD-B-013 减法后唯一来源）
     */
    private String answer;

    /**
     * 答案图 URL（DB 列 answer_img_url）
     */
    private String answerImg;

    /**
     * 解析文本（biz_text_content content_type='E'）
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
     * 标准库标注知识点（source='S'，仅详情返）
     */
    private List<QuestionKnowledgeVo> questionStdKnowledges;

    /**
     * 结构化网格块 JSON（PRD-A-015，来源 biz_question_block.block_json）。
     * null = 该题未结构化，FE 渲染回落旧富文本/图。仅详情返（列表 QuestionItemVo 不带，避免列表变重）。
     */
    private String blockJson;
}
