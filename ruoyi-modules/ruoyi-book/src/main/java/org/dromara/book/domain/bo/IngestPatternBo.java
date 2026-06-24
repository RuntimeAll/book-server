package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * upsert 题型目录入参（POST /teacher/ingest/pattern）—— PRD-C-204。
 *
 * <p>幂等键 = (anchorSubjectId, name)（DB UNIQUE uk_anchor_name）。命中则更新返回旧 id，
 * 否则新建。
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
public class IngestPatternBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 题型名（必填），UNIQUE(anchorSubjectId,name) */
    private String name;

    /** 锚知识点/小节（biz_subject.id），可空 */
    private String anchorSubjectId;

    /** 来源：'main'=书 / 'AI'（默认 'main'） */
    private String source;

    /** 排序（默认 0） */
    private Integer sort;

    /** 描述/解题套路说明 */
    private String description;

    /** 教辅 id（biz_book.id，可空） */
    private String bookId;
}
