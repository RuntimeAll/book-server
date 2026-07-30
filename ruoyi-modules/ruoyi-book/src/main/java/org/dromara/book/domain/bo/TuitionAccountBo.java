package org.dromara.book.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 开户 / 改单价入参 BO（PRD-015 D2/D3，POST /teacher/schedule/account）。
 *
 * <p>uk(student_id, subject) 冲突 = <b>改单价语义</b>（不报错、不重置余额）。
 * create_by 服务端登录态取，不信前端。
 *
 * @author backend-dev
 */
@Data
public class TuitionAccountBo {

    /** 学生 id（雪花，Jackson 由 string 兼容绑定）。 */
    private Long studentId;

    /** 学科字典码（biz_edu_subject；兼容传中文标签，服务端 normalize）。 */
    private String subject;

    /** 每课时单价（元）。 */
    private BigDecimal lessonPrice;

    /** 备注（可空）。 */
    private String note;
}
