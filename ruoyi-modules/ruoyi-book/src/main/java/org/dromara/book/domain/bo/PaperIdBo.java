package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;
import lombok.Data;

@Data
public class PaperIdBo {
    @NotBlank(message = "试卷ID不能为空")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "试卷ID必须为正整数")
    @DecimalMax(value = "9223372036854775807", message = "试卷ID超出有效范围")
    private String paperId;
}
