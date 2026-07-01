package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * PRD-C-001 — AI 组卷确定性后端接口入参 BO（POST /teacher/paper/auto-generate）。
 *
 * <p>由 Dify 前段（LLM 语言交互层）吐出的"已锁定真实 subjectId 的结构化大纲"驱动。
 * 本接口为分层架构的后段：零 LLM，纯代码确定性查库 + fallback + 组装。
 *
 * <p>字段约定：
 * <ul>
 *   <li>{@code title} 可选 — 试卷标题，缺省给默认文案</li>
 *   <li>{@code outline} 组卷大纲，每项 = 一个 (subjectId × questionType × difficult × count) 需求单元</li>
 *   <li>{@code dedup} 全局去重开关，默认 true（同一题 id 不重复选）</li>
 *   <li>{@code notesFromLLM} LLM 前段补偿透明度文案（默认填充/对齐/反问记录），原样透传到出参 notes</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
public class AutoGenerateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 试卷标题（可选；缺省走默认文案）。
     */
    private String title;

    /**
     * 组卷大纲（必填）。
     */
    private List<OutlineItem> outline;

    /**
     * 全局去重开关，默认 true。
     */
    private Boolean dedup = Boolean.TRUE;

    /**
     * LLM 前段补偿文案，透传到出参 notes。
     */
    private String notesFromLLM;

    /**
     * 是否在组卷后落库为正式试卷（写入 biz_paper）。默认 false（只组装不落库）。
     * 为 true 时必须配 {@link #teacherId}，否则不落库。
     */
    private Boolean save = Boolean.FALSE;

    /**
     * 落库归属老师 id（= sys_user.id）。C 线 Dify 免登录调用，由 book-ui 嵌入时透传当前登录老师。
     * save=true 时缺省则不落库（避免无主卷）。
     */
    private Long teacherId;

    /**
     * 大纲单项 = 一个组卷需求单元。
     */
    @Data
    public static class OutlineItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 章节-知识点编码（biz_subject.id 任意层级；经 biz_question_knowledge.knowledge_id
         * 前缀匹配递归查后代题）。LLM 前段已锁定为真实白名单内 id。
         */
        private String subjectId;

        /**
         * 章节-知识点名称（LLM 前段回填，仅用于 coverage 展示，可空）。
         */
        private String subjectName;

        /**
         * 题型 1=选择 / 4=填空 / 5=解答。
         */
        private Integer questionType;

        /**
         * 难度 1-4 星（库里有 0=未标难度；空表示不限难度）。
         */
        private Integer difficult;

        /**
         * 期望题数。
         */
        private Integer count;
    }
}
