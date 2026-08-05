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
 * 课时账本实体（biz_tuition_account，PRD-018 v3 D3 换血）。
 *
 * <p>🔄 v3 语义：一行 = 一本<b>独立账本</b>，<b>不知道学生</b>。学生×学科挂哪本账在
 * {@link BizStudentAccountLink}（n:1，共享账本 = 两条绑定同 account_id）。
 * 铁则：<b>一本账一个时薪</b>（price_per_hour），时薪不同就分本。
 *
 * <p>🔴 退役字段（DB 列仍在，代码不再读写，删列是批 4 的事）：
 * {@code student_id} / {@code subject}（绑定移到 link 表）、{@code lesson_price}（→ price_per_hour）、
 * {@code amount_remain}（金额全派生 = 小时 × price_per_hour，不落库）。
 *
 * <p>{@code hours_remain} 降级为<b>纯缓存</b>：唯一写入口仍是
 * {@code TuitionAccountService.applyFlow}，可由流水全量重算；台账「剩余」列改实时推导（D2）。
 *
 * <p>🔴 不继承 BaseEntity：本表按 PRD-015 §10.1 只有 create_by/create_time/update_time 三个审计列
 * （无 create_dept/update_by/remark），继承 BaseEntity 会让 MP 拼出不存在的列。
 * create_time/update_time 走 MetaObjectHandler 的非 BaseEntity 分支（strictInsertFill）自动填；
 * create_by 由 Service 显式置登录用户 id。
 *
 * <p>🔴 雪花主键 IdType.ASSIGN_ID（表无 AUTO_INCREMENT）。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_tuition_account")
public class BizTuitionAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 账本标签（可选，如「俊羽家」，纯展示）。 */
    private String name;

    /** 时薪（元/小时），DECIMAL(10,4)：233.3333 使 1.5h 精确派生 350.00、15h 派生 3500.00。 */
    private BigDecimal pricePerHour;

    /** 剩余小时（纯缓存，可负=欠费；两位小数）。 */
    private BigDecimal hoursRemain;

    /** 状态：'0' 正常 / '1' 停用。 */
    private String status;

    /** 备注。 */
    private String note;

    /** 归属老师。 */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
