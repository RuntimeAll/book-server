package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * POST /teacher/exam/paper/visibility 入参。
 */
@Data
public class PaperVisibilityBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 试卷 id，按字符串接收以兼容雪花 ID。 */
    @NotBlank(message = "paperId不能为空")
    private String paperId;

    /** true=发布，false=未公开。 */
    @NotNull(message = "published不能为空")
    private Boolean published;
}
