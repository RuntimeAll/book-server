package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 课时课费流水实体（biz_tuition_flow，PRD-015 D1：充值/扣课/冲正/调整一张账）。
 *
 * <p>🔴 铁律：余额只能经流水行变动，禁止裸 UPDATE 账户余额列（审计线）。
 * 每笔写入定格 hours_after / amount_after 快照 = 台账「剩余」列（历史行不因后续变动而漂移）。
 *
 * <p>uk(session_id, flow_type) = 幂等键：同一场次不可重复扣课('2')、不可重复冲正('3')。
 * 手工流水（充值 '1' / 调整 '4'）session_id 为 NULL——MySQL 唯一索引对 NULL 不去重，可多笔。
 *
 * <p>🔴 不继承 BaseEntity（本表只有 create_by/create_time 两个审计列，无 update_*），同
 * {@link BizTuitionAccount}。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_tuition_flow")
public class BizTuitionFlow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** biz_tuition_account.id。 */
    private Long accountId;

    /** 流水类型：'1' 充值 / '2' 扣课 / '3' 冲正 / '4' 调整。 */
    private String flowType;

    /** 课时增减（扣为负；两位小数=实扣课时）。 */
    private BigDecimal hoursDelta;

    /** 金额增减（扣为负）。 */
    private BigDecimal amountDelta;

    /** 本笔后剩余课时快照（台账「剩余」列，写入时定格）。 */
    private BigDecimal hoursAfter;

    /** 本笔后剩余金额快照。 */
    private BigDecimal amountAfter;

    /** 关联场次（扣课/冲正必填=幂等键；手工流水为 NULL）。 */
    private Long sessionId;

    /** 备注（充值说明 / 实际上课时间备注）。 */
    private String note;

    /** 归属老师。 */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
