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
 * 学生 × 学科 → 账本绑定实体（biz_student_account_link，PRD-018 v3 D3）。
 *
 * <p>v3 把「绑定」从账本里抽出来：{@link BizTuitionAccount} 蜕变为纯账本（不知道学生），
 * 本表一行 = 「某学生的某学科挂在哪本账、每节多少小时」。<b>n:1 不强制独占</b> ——
 * 多个学生可指向同一本账（共享账本 = 两条绑定同 account_id），系统仍无「家庭」概念。
 *
 * <p>uk(student_id, subject) = 一生一科只绑一本；idx_account 供共享本反查绑定数/学生名。
 *
 * <p>🔴 {@code hours_per_lesson} NOT NULL 且 &gt; 0（DDL DEFAULT 1.00 保底）：
 * 它既是结算缺省扣时（PRD-018 拍板 D-a），又是「节」展示的换算分母，为 0 会让扣时=0 被拒 + 折节除零。
 *
 * <p>🔴 不继承 BaseEntity：本表只有 create_by/create_time/update_time 三个审计列
 * （同 {@link BizTuitionAccount}），继承 BaseEntity 会让 MP 拼出不存在的列。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_student_account_link")
public class BizStudentAccountLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 学生 id（biz_student.id）。 */
    private Long studentId;

    /** 学科字典码（biz_edu_subject：1数学/2科学/3语文/4英语）。 */
    private String subject;

    /** 账本 id（biz_tuition_account.id）。 */
    private Long accountId;

    /** 每节时长（小时）：俊羽 1.50 / 好好 1.00。NOT NULL 且 &gt; 0。 */
    private BigDecimal hoursPerLesson;

    /** 归属老师。 */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
