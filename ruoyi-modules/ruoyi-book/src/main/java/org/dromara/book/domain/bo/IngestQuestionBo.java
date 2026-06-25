package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 录一道题入参（POST /teacher/ingest/question，事务写多表）—— PRD-C-204 B1。
 *
 * <p>幂等键 = externalKey（service 内存 md5 至 biz_question.stem_hash 做去重判存在）。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
public class IngestQuestionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 幂等键（book+节+课时+刷X+题号），去重 */
    private String externalKey;

    /** 导入批次 id */
    private String importBatchId;

    /** 导入来源（'main'） */
    private String importSource;

    /** 科目锚 level1（年级根 100），NOT NULL */
    private String subjectId;

    /** 题型 1选择 2填空 3判断 4计算 5解答 6作图，NOT NULL */
    private Integer questionType;

    /** 难度 1基础 2提升 3压轴，NOT NULL */
    private Integer difficult;

    /** 压轴标记 0/1 */
    private Integer isAnchor;

    /** §2a 合法 blockJson（{v:1,rows:[...]}），图块 url=OSS */
    private String blockJson;

    /** 纯文本题干（全文检索） */
    private String stemText;

    /** 答案文本 → biz_text_content content_type='A' */
    private String answerText;

    /** 解析文本 → biz_text_content content_type='E' */
    private String analyzeText;

    /** 锚知识点叶子 → biz_question_knowledge */
    private List<KnowledgeRef> knowledgeIds;

    /** 题图 → biz_question_image */
    private List<ImageRef> images;

    /** 教辅编排 → biz_book_question */
    private BookRef book;

    /** 题型关联（可选）→ biz_question_pattern_rel（按 uk_q_p upsert）—— PRD-C-204 */
    private List<PatternRef> patterns;

    /** 出处年份 */
    private String examYear;

    /** 区域编码 */
    private String regionCode;

    /** 来源类型 */
    private Integer sourceType;

    /** 出处原文 */
    private String sourceRaw;

    /** 母题来源 */
    private String motherSource;

    /**
     * 题目状态：'0'草稿 / '1'已发布 / '2'软删（PRD-A-002 录题闭环）。
     * 缺省 null → service 兜底 '1'（不破 PRD-C-204 老调用方）；路A/路B 录题传 '0' 落草稿。
     */
    private String status;

    @Data
    public static class KnowledgeRef implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 知识点 biz_subject.id 叶子 */
        private String kpId;
        /** 是否主考点 0/1 */
        private Integer isPrimary;
        /** 来源 U=用户 / S=标准库 */
        private String source;
        private BigDecimal confidence;
    }

    @Data
    public static class ImageRef implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long assetId;
        private String ossUrl;
        private String blockId;
        /** stem/answer/figure */
        private String role;
        private Integer seq;
        private Integer isDecorative;
    }

    @Data
    public static class BookRef implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String bookId;
        /** section/lesson 等 */
        private String containerType;
        private String containerId;
        /** 刷基础/刷提升 */
        private String columnType;
        private String bookDifficulty;
        private String role;
        private Integer inBlockSeq;
    }

    /** 题型关联引用（PRD-C-204）→ biz_question_pattern_rel */
    @Data
    public static class PatternRef implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 题型 id（biz_question_pattern.id，必填，需先经 /ingest/pattern 建好） */
        private Long patternId;
        /** 是否主题型：1=主 / 0=次（默认 1） */
        private Integer isPrimary;
        /** 来源：'main' / 'AI'（默认 'main'） */
        private String source;
    }
}
