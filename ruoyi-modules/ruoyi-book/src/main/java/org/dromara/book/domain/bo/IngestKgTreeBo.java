package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 知识图谱整树 upsert 入参（POST /teacher/ingest/kg/tree）—— PRD-C-204 B1。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
public class IngestKgTreeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点列表（整树一次灌） */
    private List<Node> nodes;

    /** 单个 biz_subject 节点 */
    @Data
    public static class Node implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 层级编码（每 3 位一层） */
        private String id;
        private String parentId;
        private String name;
        private Integer level;
        private Integer sort;
    }
}
