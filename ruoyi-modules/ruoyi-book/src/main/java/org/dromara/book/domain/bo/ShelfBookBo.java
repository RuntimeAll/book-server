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

    /**
     * 公开标记（1=公开可读，0=仅 owner）。🔴 只在 updateBook 生效且**仅超级管理员可改**，
     * createBook 一律建成私有（0）——公开是审定后的独立动作，不能随建书顺手带上。
     */
    private Integer isPublic;

    /** 编排风格元数据（Object，Service 序列化为 JSON 文本） */
    private Object styleMeta;

    private Long sourceJobId;

    private String remark;
}
