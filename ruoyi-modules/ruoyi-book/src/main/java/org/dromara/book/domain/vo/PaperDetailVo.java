package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * POST /teacher/exam/paper/detail 响应 VO（E 卡段② —— 试卷详情独立页）。
 *
 * <p>跟 GET /teacher/paper/source/{id}（B-10 原卷预览）的区别：
 * <ul>
 *   <li>原卷预览（PaperSourceVo）：扁平 questions[] —— 不分大题</li>
 *   <li>试卷详情（PaperDetailVo）：sections[].questions[] —— 按大题分组</li>
 * </ul>
 *
 * <p>契约（段①数据体检报告 §4）：
 * <pre>
 * {
 *   "paperId": 2798,
 *   "paperName": "2025年嘉兴一中实验学校七年级（上）期末数学试卷",
 *   "subjectId": "3001004004",
 *   "score": 120.00,
 *   "suggestTime": 120,
 *   "questionCount": 24,
 *   "examYear": "2025",
 *   "paperType": 1,
 *   "sections": [
 *     {"sectionId": 3678, "title": "选择题", "sort": 1, "questions": [...]},
 *     {"sectionId": 3679, "title": "填空题", "sort": 3, "questions": [...]},
 *     {"sectionId": 3680, "title": "简答题", "sort": 4, "questions": [...]}
 *   ]
 * }
 * </pre>
 *
 * <p>边界：paper 不存在 / status≠'1' 软删 / 草稿 → service 返 null（advice 包 envelope 输出 null）。
 *
 * @author backend-dev (E 卡段②)
 */
@Data
public class PaperDetailVo implements Serializable {

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
     * 卷分类编码（biz_paper.subject_id —— 教材冗余字段）
     */
    private String subjectId;

    /**
     * 卷总分（biz_paper.score DECIMAL(6,2)）
     */
    private BigDecimal score;

    /**
     * 建议时长（分钟，biz_paper.suggest_time）
     */
    private Integer suggestTime;

    /**
     * 题数（冗余字段，biz_paper.question_count）
     */
    private Integer questionCount;

    /**
     * 考试年份（biz_paper.exam_year，nullable）
     */
    private String examYear;

    /**
     * 卷类型（biz_paper.paper_type —— 1=手工 2=自动 6=专题）
     */
    private Integer paperType;

    /**
     * 大题分组（按 biz_paper_section.sort ASC 排序；每个 section 内的 questions 按
     * biz_paper_question.sort ASC 排序 —— 跨 section 全局连续题号）
     */
    private List<PaperSectionVo> sections;
}
