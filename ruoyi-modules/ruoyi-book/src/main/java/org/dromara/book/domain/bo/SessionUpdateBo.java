package org.dromara.book.domain.bo;

import lombok.Data;

/** 场次通用改入参（PRD-C-213 PUT /teacher/schedule/session/{id}）。改期不触发顺延。 */
@Data
public class SessionUpdateBo {
    private String date;
    private String start;
    private String end;
    private String note;
    /** 学科改配（字典 biz_edu_subject；兼容中文标签） */
    private String subject;
    /** rebind：改绑课次（只改本场） */
    private Long planLessonId;
}
