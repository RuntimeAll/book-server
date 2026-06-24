package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 知识点内容块 upsert 入参（POST /teacher/ingest/kg/content）—— PRD-C-204 B1。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
public class IngestKgContentBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识点 biz_subject.id */
    private String subjectId;

    /** 内容块列表 */
    private List<Block> blocks;

    /** 单个内容块（重点/难点/方法/注意/拓展/说明 + 文本 + 可选配图） */
    @Data
    public static class Block implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 块类型：重点|难点|方法|注意|拓展|说明 */
        private String kind;
        private String text;
        /** 可选配图 OSS URL */
        private String imgUrl;
    }
}
