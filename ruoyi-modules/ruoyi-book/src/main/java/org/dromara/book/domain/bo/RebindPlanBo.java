package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 换绑计划入参（PRD-C-213 R1a：POST /teacher/schedule/target/{targetType}/{targetId}/rebind-plan）。
 *
 * @author backend-dev
 */
@Data
public class RebindPlanBo {
    /** 新计划 id（必传；须归属该对象，S1 校验） */
    private Long newPlanId;
}
