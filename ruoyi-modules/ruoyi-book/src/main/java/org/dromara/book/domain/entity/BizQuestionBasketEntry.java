package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_question_basket_entry")
public class BizQuestionBasketEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String namespace;
    private String entryKey;
    private Long questionId;
    private Long sourceBookId;
    private Long sourceItemId;
    private String snapshotJson;
}
