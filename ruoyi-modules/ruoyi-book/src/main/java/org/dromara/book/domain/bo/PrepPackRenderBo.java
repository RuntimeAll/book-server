package org.dromara.book.domain.bo;

import lombok.Data;

/** 备课包渲染入参（PRD-C-213 POST /teacher/schedule/prep-pack/{id}/render）。 */
@Data
public class PrepPackRenderBo {
    /** 指定段序号（0 基）；空=全部段 */
    private Integer segIndex;
    /** 全段成功后是否置"已备好"并联动双态 */
    private Boolean markReady = true;
}
