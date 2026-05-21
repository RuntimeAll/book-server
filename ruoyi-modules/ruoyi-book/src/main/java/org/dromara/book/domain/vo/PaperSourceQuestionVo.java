package org.dromara.book.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * GET /teacher/paper/source/{id} 内 questions 数组元素 VO（PRD §3.1 B-10）。
 *
 * <p>继承 {@link QuestionDetailVo} 完整复用题字段（含 page 字段 + 详情字段），
 * 新增 {@code sort}（题在卷内顺序）+ {@code pqScore}（biz_paper_question.score，
 * 真分值，不同于 QuestionItemVo.score 的列表占位 0）。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code optionsJson} 沿用父类 String 透传（FE 自行 JSON.parse，BE 不反序列化）</li>
 *   <li>{@code scoreStd} 沿用父类 String 透传（虽然原卷预览 FE 当前不用，但保留契约一致）</li>
 *   <li>{@code sort} 取自 biz_paper_question.sort（NOT NULL，升序排）</li>
 *   <li>{@code pqScore} 取自 biz_paper_question.score（DECIMAL(5,2)，真分值）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaperSourceQuestionVo extends QuestionDetailVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题在卷内顺序（biz_paper_question.sort，NOT NULL）
     */
    private Integer sort;

    /**
     * 题在卷内分值（biz_paper_question.score DECIMAL(5,2)，NULLABLE）
     */
    private BigDecimal pqScore;
}
