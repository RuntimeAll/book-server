package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 书架·书 增改入参（PRD-002）。JSON 字段以 Object 收，Service 侧序列化。
 *
 * @author backend-dev
 */
@Data
public class ShelfBookBo {

    private Long id;

    private String bookType;

    private String title;

    private String subjectId;

    private String grade;

    private String edition;

    private String status;

    /** 编排风格元数据（Object，Service 序列化为 JSON 文本） */
    private Object styleMeta;

    private Long sourceJobId;

    private String remark;
}
