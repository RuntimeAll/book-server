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
 * 课时课费账户实体（biz_tuition_account，PRD-015 D2/D3）。
 *
 * <p>一行 = 一个「学生 × 学科」绑定（uk_student_subject）——<b>开户即绑定学科</b>，
 * 不另建 student_subject 关系表；余额可为 0 或负数（欠费不拦截，老师最高权限）。
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

    /** 学生 id（biz_student.id）。 */
    private Long studentId;

    /** 学科字典码（biz_edu_subject：1数学/2科学/3语文/4英语）。 */
    private String subject;

    /** 每课时单价（元）。 */
    private BigDecimal lessonPrice;

    /** 剩余课时（可负=欠费；两位小数，D5 实扣 0.67 等）。 */
    private BigDecimal hoursRemain;

    /** 剩余金额（元；可负）。 */
    private BigDecimal amountRemain;

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
