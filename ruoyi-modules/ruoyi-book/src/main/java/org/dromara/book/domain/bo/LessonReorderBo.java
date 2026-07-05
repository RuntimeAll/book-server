package org.dromara.book.domain.bo;

import lombok.Data;
import java.util.List;

/** 课次重排入参（PRD-C-213 /teacher/schedule/plan/{id}/lessons/reorder）。按 lessonIds 顺序重写 lesson_seq。 */
@Data
public class LessonReorderBo {
    private List<Long> lessonIds;
}
