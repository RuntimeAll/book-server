package org.dromara.book.domain.bo;

import lombok.Data;

/** 课程计划建/改入参（PRD-C-213 /teacher/schedule/plan）。 */
@Data
public class CoursePlanBo {
    private Long id;
    private String name;
    private String targetType;
    private String termTag;
    private Integer year;
    private String materialNote;
    /** 默认分段模板（数组 JSON） */
    private Object defaultSegTemplate;
    private String status;
}
