package org.dromara.book.domain.entity;

import lombok.Data;

@Data
public class BizPaperCreateRequest {
    private Long userId;
    private String requestId;
    private String payloadHash;
    private Long paperId;
    private Integer questionCount;
}
