package org.dromara.book.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账本间转账入参 BO（PRD-018 M6，POST /teacher/schedule/account/transfer）。
 *
 * <p>换本（时薪变了要开新本）/ 拆本（共享本拆回两户）在台账上必须是<b>一笔可解释的动作</b>：
 * 一个事务产一对 '4' 调整行（转出为负、转入为正）并互写 {@code rel_flow_id}，
 * 而不是老师手搓两笔互不相干的调整。
 *
 * @author backend-dev
 */
@Data
public class TuitionTransferBo {

    /** 转出账本 id。 */
    private Long fromAccountId;

    /** 转入账本 id。 */
    private Long toAccountId;

    /** 转移小时数（必须 &gt; 0）。 */
    private BigDecimal hours;

    /** 业务日期（yyyy-MM-dd；默认今天）。 */
    private String occurDate;

    /** 备注（两行共用，默认「账本转移」）。 */
    private String note;
}
