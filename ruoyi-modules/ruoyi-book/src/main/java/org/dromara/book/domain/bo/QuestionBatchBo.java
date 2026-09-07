package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Explicit detail/export selection, not a book browsing query. */
@Data
public class QuestionBatchBo {
    public static final int MAX_IDS = 100;

    @NotNull
    @Size(max = MAX_IDS)
    private List<@NotNull @Positive Long> ids;
}
