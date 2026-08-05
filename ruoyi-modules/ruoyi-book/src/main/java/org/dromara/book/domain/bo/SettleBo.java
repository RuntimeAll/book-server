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
     * ⛔ <b>已废弃且被忽略</b>（PRD-018 D10 域间解耦，2026-08-05）：结算不再副作用式建反馈壳，
     * 反馈单独立建单。字段保留只为让旧 FE/H5/机器人继续传值时<b>不报错</b>（M5 兼容），
     * 传 true / false / 不传，行为完全一致。批 3/4 各端跟随后可删。
     */
    @Deprecated
    private Boolean genFeedback;
}
