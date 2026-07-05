package org.dromara.book.domain.bo;

import lombok.Data;

/** 课程计划建/改入参（PRD-C-213 /teacher/schedule/plan）。R1a·S1：建计划必传 targetType+targetId。 */
@Data
public class CoursePlanBo {
    private Long id;
    private String name;
    private String targetType;
    /** S1 计划归属对象 id（建计划必传） */
    private Long targetId;
    private String termTag;
    private Integer year;
    private String materialNote;
    /** 默认分段模板（数组 JSON） */
    private Object defaultSegTemplate;
    private String status;
}
