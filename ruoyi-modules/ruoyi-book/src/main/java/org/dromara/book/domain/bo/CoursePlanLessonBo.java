package org.dromara.book.domain.bo;

import lombok.Data;

/** 计划课次 upsert 单元（id 空=新增；PRD-C-213）。 */
@Data
public class CoursePlanLessonBo {
    private Long id;
    private Integer lessonSeq;
    private String title;
    private String lessonType;
    private String tag;
    private String sourceRef;
    private String thinkingAction;
    private String layerTarget;
    private String parentCopy;
    /** KG 锚点数组 JSON */
    private Object kgNodeIds;
    /** 分段模板数组 JSON */
    private Object segTemplate;
    private String prepState;
}
