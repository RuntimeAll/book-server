package org.dromara.book.domain.bo;

import lombok.Data;

import java.util.List;

/**
 * 一键结算入参 BO（PRD-015 D4/D5，POST /teacher/schedule/settle）。
 *
 * <p>🔴 只提醒不自动扣（D4）：扣费唯一入口就是本请求，老师确认后才动账。
 * 逐场独立事务——某场失败（未开户/已结算）只该场 skipped，其余照常结算。
 *
 * @author backend-dev
 */
@Data
public class SettleBo {

    /** 要结算的场次列表（每项含实扣课时与时间备注）。 */
    private List<SettleItemBo> items;

    /**
     * 是否同时生成反馈壳（D12，弹窗默认勾选）。
     * 🔴 不传 = true（与 FE 默认勾选一致）；仅显式 false 才不建壳。
     */
    private Boolean genFeedback;
}
