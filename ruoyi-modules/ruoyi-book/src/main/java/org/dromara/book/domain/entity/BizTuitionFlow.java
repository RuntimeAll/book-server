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
import java.time.LocalDate;
import java.util.Date;

/**
 * 课时流水实体（biz_tuition_flow，PRD-015 D1 一张账 + PRD-018 v3 换轨）。
 *
 * <p>🔴 铁律：余额只能经流水行变动，禁止裸 UPDATE 账户余额列（审计线）。
 *
 * <p>🔄 v3 换轨（PRD-018 D2）：<b>快照列退役</b> —— {@code hours_after}/{@code amount_after}
 * 是乱序补录的根源，台账「剩余」改实时推导（按 occur_date 正序逐行累加）；
 * {@code amount_delta} 退役 —— 金额 = 小时 × price_per_hour 全程派生。三列 DB 里仍在，
 * 代码不再读写（删列是批 4 的事）。
 *
 * <p>新列：
 * <ul>
 *   <li>{@code occur_date} 业务日期 = 台账排序键（充值/调整由入参给、默认今天，可回填历史；
 *       扣课/冲正取所属场次日期）。🔴 读取口径：带 session_id 的行日期与同日次序<b>一律以
 *       session.session_date + start_time 为准</b>（单一事实源，改期后台账自动跟随，M4 甲案），
 *       本列只作落库粗筛。</li>
 *   <li>{@code amount_paid} 实收/冻结金额：充值/调整行 = 入参原样（收 3500 记 3500，M9）；
 *       扣课/冲正行 = 结算当时派生金额（拍板 B2 历史冻结，改时薪不重算历史）。符号与
 *       hours_delta 同向（扣课为负、冲正为正）。</li>
 *   <li>{@code rel_flow_id} 对手方引用：换本/拆本 transfer 产的一对 '4' 调整行互指（M6）。</li>
 * </ul>
 *
 * <p>uk(session_id, flow_type) = 幂等键 🔴 <b>绝不动</b>：同一场次不可重复扣课('2')、不可重复冲正('3')。
 * 手工流水（充值 '1' / 调整 '4'）session_id 为 NULL——MySQL 唯一索引对 NULL 不去重，可多笔。
 *
 * <p>🔴 不继承 BaseEntity（本表只有 create_by/create_time 两个审计列，无 update_*），同
 * {@link BizTuitionAccount}。{@code create_time} 降级为<b>纯审计</b>，不再参与台账排序。
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

    /** 业务日期（台账排序键；过渡期可为 NULL，推导侧用 COALESCE(occur_date, DATE(create_time)) 兜底）。 */
    private LocalDate occurDate;

    /** 小时增减（扣为负；两位小数）。 */
    private BigDecimal hoursDelta;

    /** 实收/冻结金额（元，符号同 hours_delta；见类注释）。 */
    private BigDecimal amountPaid;

    /** 对手方流水 id（transfer 一对 '4' 调整行互指）。 */
    private Long relFlowId;

    /** 关联场次（扣课/冲正必填=幂等键；手工流水为 NULL）。 */
    private Long sessionId;

    /** 备注（充值说明 / 实际上课时间备注）。 */
    private String note;

    /** 归属老师。 */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
