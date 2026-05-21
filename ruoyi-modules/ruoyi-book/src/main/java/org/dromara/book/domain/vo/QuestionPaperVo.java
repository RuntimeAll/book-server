package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * GET /teacher/qd/papers/{id} 响应单元素 VO（PRD §3.1 B-6）。
 *
 * <p>契约：题在哪些卷 → {@code [{examPaperId, examPaperName, examYear?, sort?}]}
 *
 * <p>SQL 列 → VO 字段映射：
 * <ul>
 *   <li>biz_paper_question.paper_id → examPaperId（misikt 真实 FE 契约字段名仍叫 examPaperId）</li>
 *   <li>biz_paper.name              → examPaperName</li>
 *   <li>biz_paper.exam_year         → examYear（nullable）</li>
 *   <li>biz_paper_question.sort     → sort（题在卷内顺序）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
public class QuestionPaperVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 试卷 id（DB 列 paper_id；FE 契约字段名 examPaperId 沿用 misikt）
     */
    private Long examPaperId;

    /**
     * 试卷名
     */
    private String examPaperName;

    /**
     * 考试年份（biz_paper.exam_year，nullable）
     */
    private String examYear;

    /**
     * 题在卷内顺序（biz_paper_question.sort，nullable）
     */
    private Integer sort;
}
