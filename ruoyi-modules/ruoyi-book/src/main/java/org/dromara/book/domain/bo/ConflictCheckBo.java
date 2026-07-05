package org.dromara.book.domain.bo;

import lombok.Data;
import java.util.List;

/** 冲突预检入参（PRD-C-213 /teacher/schedule/session/conflict-check）。 */
@Data
public class ConflictCheckBo {
    private String targetType;
    private Long targetId;
    private List<SessionItemBo> items;
}
