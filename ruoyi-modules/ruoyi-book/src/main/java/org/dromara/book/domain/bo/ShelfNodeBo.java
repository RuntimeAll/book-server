package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 书架·节点 增改入参（PRD-002）。
 *
 * @author backend-dev
 */
@Data
public class ShelfNodeBo {

    private Long id;

    private Long bookId;

    private Long parentId;

    private Integer seq;

    private String nodeType;

    private String name;

    private Long kpId;

    /** 节点元数据（Object，Service 序列化为 JSON 文本） */
    private Object meta;
}
