package org.dromara.book.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Bounded, direct-node reading page. Descendants are separate resources. */
@Data
public class ShelfItemPageBo {
    @NotNull
    @Min(1)
    private Integer pageNum = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 20;
}
