package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 场次通用改入参（PRD-C-213 PUT /teacher/schedule/session/{id}）。
 * 🔄 PRD-018 D6：顺延整套已删，改期只改本场；新增 content。
 */
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
    /** 这节课实际讲了什么（PRD-018 ③，≤200 字；台账「内容」列首选取值） */
    private String content;
}
