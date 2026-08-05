package org.dromara.book.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工流水入参 BO（PRD-015 + PRD-018 D9，POST /teacher/schedule/account/{id}/flow）。
 *
 * <p>🔴 手工只允许 '1' 充值 / '4' 调整；'2' 扣课 / '3' 冲正由结算链服务端产生，
 * 前端传入一律 400（防绕开幂等键手写扣课行）。
 *
 * <p><b>三档输入（D9）</b>：{@code hours} / {@code lessons} / {@code amount} 任给其一，服务端换算成小时落库——
 * <ul>
 *   <li>lessons × link.hours_per_lesson（该账本单绑学生的每节时长；共享本多绑时不接受 lessons 档）</li>
 *   <li>amount ÷ account.price_per_hour</li>
 * </ul>
 * {@code occurDate} = 业务日期（默认今天，补录可回填历史）；
 * {@code amountPaid} = 实收金额原样落库（M9：收 3500 就记 3500，不用派生值倒推）——
 * 不显式给时，走 amount 档则取 amount。
 *
 * <p>🔴 过渡期兼容（M5）：旧 FE/机器人传的 {@code hoursDelta} 等价 hours；
 * {@code amountDelta}（已退役的金额增量列）<b>静默忽略不报错</b>，避免旧调用方 400。
 *
 * @author backend-dev
 */
@Data
public class TuitionFlowBo {

    /** 流水类型：'1' 充值 / '4' 调整。 */
    private String flowType;

    /** ① 小时档：小时增量（正=加，负=扣）。 */
    private BigDecimal hours;

    /** ② 节档：节数增量（× 该绑定的每节时长换算成小时）。 */
    private BigDecimal lessons;

    /** ③ 金额档：金额增量（÷ 账本时薪换算成小时）。 */
    private BigDecimal amount;

    /** 实收金额（元，原样落库；不给则金额档取 amount）。 */
    private BigDecimal amountPaid;

    /** 业务日期（yyyy-MM-dd；默认今天，补录可回填）。 */
    private String occurDate;

    /** 备注（台账「内容」列对充值/调整行显示的就是它）。 */
    private String note;

    /** 🔴 旧契约兼容：等价 hours。 */
    private BigDecimal hoursDelta;

    /** 🔴 旧契约兼容：已退役的金额增量列，静默忽略（不当作 amount 档，避免重复换算）。 */
    private BigDecimal amountDelta;
}
