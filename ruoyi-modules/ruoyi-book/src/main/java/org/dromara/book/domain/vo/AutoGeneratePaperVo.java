package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PRD-C-001 — AI 组卷确定性后端接口出参 VO（POST /teacher/paper/auto-generate 的 response）。
 *
 * <p>结构：
 * <pre>
 * {
 *   paper:    { title, totalCount, sections:[{title, questionType, questions:[QuestionItemVo]}] },
 *   coverage: { "subjectId(name)" -> 命中题数 },
 *   tips:     规则文案,
 *   gaps:     [{subjectId, questionType, difficult, want, got, reason}],
 *   notes:    LLM 前段补偿文案透传
 * }
 * </pre>
 *
 * <p>题全部来自真实题库（{@link QuestionItemVo#getId()} 可溯源 biz_question.id），非编造。
 *
 * @author backend-dev
 */
@Data
public class AutoGeneratePaperVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 组装好的试卷。
     */
    private Paper paper;

    /**
     * 覆盖统计：key = subjectId 或 "subjectId(subjectName)"，value = 命中题数。
     */
    private Map<String, Integer> coverage = new LinkedHashMap<>();

    /**
     * 规则文案（去重/fallback/缺口情况的人读提示）。
     */
    private String tips;

    /**
     * 缺口记录（fallback L3 仍不够时透明记录，宁可少给不跨章节硬凑）。
     */
    private List<Gap> gaps;

    /**
     * LLM 前段补偿文案透传（notesFromLLM）。
     */
    private String notes;

    /**
     * 落库后的试卷 id（仅当入参 save=true 且 teacherId 有效时有值，否则 null）。
     */
    private Long paperId;

    /**
     * 落库后的卷详情深链（book-ui /papers/source/{paperId}），供老师一键打开；未落库为 null。
     */
    private String paperUrl;

    /**
     * 试卷主体。
     */
    @Data
    public static class Paper implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 试卷标题。
         */
        private String title;

        /**
         * 实际选出题目总数。
         */
        private Integer totalCount;

        /**
         * 按题型分组的 sections（顺序 1=选择 → 4=填空 → 5=简答），复用 {@link ExamSectionVo}。
         */
        private List<ExamSectionVo> sections;
    }

    /**
     * 单条缺口记录。
     */
    @Data
    public static class Gap implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 缺口对应的章节-知识点编码。
         */
        private String subjectId;

        /**
         * 缺口对应题型。
         */
        private Integer questionType;

        /**
         * 缺口对应难度（null = 已放宽难度仍不够）。
         */
        private Integer difficult;

        /**
         * 期望题数。
         */
        private Integer want;

        /**
         * 实际可给题数。
         */
        private Integer got;

        /**
         * 缺口原因说明。
         */
        private String reason;
    }
}
