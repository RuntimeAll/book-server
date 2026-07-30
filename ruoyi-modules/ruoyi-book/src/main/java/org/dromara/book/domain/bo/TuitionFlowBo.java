package org.dromara.book.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工流水入参 BO（PRD-015，POST /teacher/schedule/account/{id}/flow）。
 *
 * <p>🔴 手工只允许 '1' 充值 / '4' 调整；'2' 扣课 / '3' 冲正由结算链服务端产生，
 * 前端传入一律 400（防绕开幂等键手写扣课行）。
 * 课时与金额两笔增量独立传，服务端不代算（充值 100 元送 1 课时这类口径由老师自己定）。
 *
 * @author backend-dev
 */
@Data
public class TuitionFlowBo {

    /** 流水类型：'1' 充值 / '4' 调整。 */
    private String flowType;

    /** 课时增量（正=加课时，负=扣）。 */
    private BigDecimal hoursDelta;

    /** 金额增量（元，正=进账，负=出账）。 */
    private BigDecimal amountDelta;

    /** 备注（台账「上课内容」列对充值/调整行显示的就是它）。 */
    private String note;
}
