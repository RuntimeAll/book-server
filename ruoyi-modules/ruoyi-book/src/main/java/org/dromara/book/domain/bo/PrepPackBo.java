package org.dromara.book.domain.bo;

import lombok.Data;

/** 备课包建包入参（PRD-C-213 POST /teacher/schedule/prep-pack）。planLessonId 与 sessionId 二选一。 */
@Data
public class PrepPackBo {
    private Long planLessonId;
    private Long sessionId;
    /** 分段内容数组 JSON：[{name,style,question_ids:[str],rules,note}] */
    private Object segs;
}
