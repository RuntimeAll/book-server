package org.dromara.book.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class BasketEntryVo {
    private String entryKey;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long basketEntryId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long questionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceBookId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceItemId;
    private QuestionDetailVo question;
}
