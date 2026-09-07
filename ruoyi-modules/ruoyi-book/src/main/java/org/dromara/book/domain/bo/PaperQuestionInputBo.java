package org.dromara.book.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaperQuestionInputBo extends SelectionReferenceBo {
    private String basketNamespace;
    private Long basketEntryId;
    private Integer sort;
    private BigDecimal score;
}
