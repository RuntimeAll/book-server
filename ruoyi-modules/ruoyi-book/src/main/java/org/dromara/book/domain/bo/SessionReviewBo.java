package org.dromara.book.domain.bo;

import lombok.Data;
import java.util.List;

/** 课后回收入参（PRD-C-213 POST /teacher/schedule/session/{id}/review）。 */
@Data
public class SessionReviewBo {
    /** 逐题结果 [{question_id?,seg,seq,result,cause}] */
    private List<Object> itemResults;
    private String teacherNote;
    /** LLM 润色位：传入则用之覆盖模板家长消息 */
    private String parentMsgOverride;
}
