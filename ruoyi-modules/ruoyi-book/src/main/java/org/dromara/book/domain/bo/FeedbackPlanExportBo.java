package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 按计划导出反馈图入参（PRD-015 D13，POST /teacher/feedback/export-plan-png）。
 *
 * <p>模式记忆在 FE（localStorage），接口无状态：mode 缺省 = 'single'。
 * planId 雪花 —— FE/MCP 一律以字符串传，Jackson 兜住 string→Long。
 *
 * @author backend-dev
 */
@Data
public class FeedbackPlanExportBo {

    /** 课程计划 id（必填）。 */
    private Long planId;

    /** 'single' = 最新一单单张（缺省）；'long' = 全量按序号升序拼长图。 */
    private String mode;
}
