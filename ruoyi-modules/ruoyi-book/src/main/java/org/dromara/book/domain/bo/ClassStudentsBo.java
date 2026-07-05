package org.dromara.book.domain.bo;

import lombok.Data;
import java.util.List;

/** 设班课成员入参（PRD-C-213 /teacher/schedule/class/{id}/students）。 */
@Data
public class ClassStudentsBo {
    private List<Long> studentIds;
}
