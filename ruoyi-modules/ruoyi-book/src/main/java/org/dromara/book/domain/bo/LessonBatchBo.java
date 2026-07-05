package org.dromara.book.domain.bo;

import lombok.Data;
import java.util.List;

/** 批量 upsert 课次入参（PRD-C-213 /teacher/schedule/plan/{id}/lessons）。 */
@Data
public class LessonBatchBo {
    private List<CoursePlanLessonBo> lessons;
}
