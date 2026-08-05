package org.dromara.book.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 结算单项 BO（PRD-015 D5，POST /teacher/schedule/settle 的 items 元素）。
 *
 * <p>一项 = 一场课的结算口径：实扣课时（缺省 1，支持两位小数 0.5/0.67/1.5…）
 * + 实际上课时间备注（进流水 note，台账「上课内容」列可见）。
 * 金额 = 实扣课时 × 该生该科账户单价，服务端算，前端不传金额。
 *
 * @author backend-dev
 */
@Data
public class SettleItemBo {

    /** 场次 id（雪花，FE 以字符串传，Jackson 反序列化成 Long）。 */
    private Long sessionId;

    /** 实扣课时；不传 = 1 课时（D5 默认一场一课时）。 */
    private BigDecimal hours;

    /** 实际上课时间备注（如「09:05-10:40」），写入扣课流水 note。 */
    private String timeNote;

    /**
     * 这节课实际讲了什么（PRD-018 ③，≤200 字，可选）。
     *
     * <p>结算时顺手记 → 写进 {@code session.content}，台账「内容」列优先取它
     * （content → 课次标题 → 「正课」）。不传 = 不动原值。
     */
    private String content;
}
