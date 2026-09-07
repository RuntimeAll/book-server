package org.dromara.book.domain.bo;

import lombok.Data;

@Data
public class SelectionReferenceBo {
    private Long questionId;
    private Long sourceBookId;
    private Long sourceItemId;
}
