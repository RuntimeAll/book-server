package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审核页「就地改题」入参（PUT /teacher/ingest/job/{jobId}/item/{itemId}）—— PRD-A-002 路B B5。
 *
 * <p>老师在审核页改拆错的题面/答案/解析/题型，存回 biz_ingest_job_item（未入库前的暂存编辑），
 * 入库时 commit 读改后的值。仅改给定的非 null 字段（局部更新）。
 *
 * @author backend-dev (PRD-A-002 路B B5)
 */
@Data
public class IngestJobItemEditBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 题干（Markdown+$LaTeX$），非 null 才改 */
    private String stemText;

    /** 答案，非 null 才改 */
    private String answerText;

    /** 解析，非 null 才改 */
    private String analyzeText;

    /** 题型 1选择/2填空/5解答，非 null 才改 */
    private Integer questionType;
}
