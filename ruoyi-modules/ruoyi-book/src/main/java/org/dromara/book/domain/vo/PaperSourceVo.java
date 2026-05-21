package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * GET /teacher/paper/source/{id} 响应 VO（PRD §3.1 B-10 — 原卷预览）。
 *
 * <p>契约：{@code {paperId, paperName, examYear?, questions: [{...题字段, sort}]}}
 *
 * <p>业务：给定 paperId，返该卷的"卷头信息（id/name/exam_year）+ 该卷下所有已发布题"
 * （按 sort 升序）。
 *
 * <p>边界：
 * <ul>
 *   <li>paper 不存在 / status≠'1' → service 返 {@code null}（advice 包 envelope 输出 null）</li>
 *   <li>paper 存在但 0 题 → 返 {@code {paperId, paperName, examYear, questions: []}}（不返 null）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
public class PaperSourceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 试卷 id（biz_paper.id）
     */
    private Long paperId;

    /**
     * 试卷名（biz_paper.name）
     */
    private String paperName;

    /**
     * 考试年份（biz_paper.exam_year，nullable）
     */
    private String examYear;

    /**
     * 卷下所有已发布题（按 biz_paper_question.sort 升序）
     */
    private List<PaperSourceQuestionVo> questions;
}
