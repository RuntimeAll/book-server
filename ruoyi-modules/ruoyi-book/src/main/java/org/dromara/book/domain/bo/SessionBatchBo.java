package org.dromara.book.domain.bo;

import lombok.Data;
import java.util.List;

/** 批量排课入参（PRD-C-213 /teacher/schedule/session/batch）。 */
@Data
public class SessionBatchBo {
    private String targetType;
    private Long targetId;
    private Long planId;
    /** autoBind=按 lesson_seq 顺序自动绑未排课次 */
    private Boolean autoBind = true;
    /** 命中冲突时是否强存 */
    private Boolean force = false;
    private List<SessionItemBo> items;
}
