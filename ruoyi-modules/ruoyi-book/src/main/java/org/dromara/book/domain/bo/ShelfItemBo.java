package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 书架·内容项 增改入参（PRD-002）。
 *
 * <p>questionId 用 String 收避免雪花号 JSON number 精度截尾（create-paper 同款坑）；Service 侧转 Long。
 *
 * @author backend-dev
 */
@Data
public class ShelfItemBo {

    private Long id;

    private Long nodeId;

    /** explain 讲解块 / question 题引用 */
    private String kind;

    /** 题 id（字符串收，防雪花号截尾） */
    private String questionId;

    private Integer seq;

    /** 书内改题副本（Object，Service 序列化） */
    private Object override;

    /** 讲解块内容（Object，Service 序列化） */
    private Object explain;
}
