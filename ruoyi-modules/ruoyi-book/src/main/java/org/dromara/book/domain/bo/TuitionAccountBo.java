package org.dromara.book.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 开户 / 改本入参 BO（PRD-015 D2/D3 + PRD-018 v3 D3，POST /teacher/schedule/account）。
 *
 * <p>🔄 v3：账本与绑定拆开，但<b>开户入口仍是一步无感知</b>——传 studentId + subject +
 * 时薪 + 每节时长，服务端同一事务里「建账本 + 建绑定」。
 * uk(student_id, subject) 冲突（已绑过该学科）= <b>改绑定/改时薪语义</b>（不报错、不动余额）。
 * create_by 服务端登录态取，不信前端。
 *
 * <p>🔴 过渡期兼容（M5）：旧 FE/H5/机器人仍传 {@code lessonPrice}（元/节）。
 * 服务端口径 = {@code pricePerHour} 优先；只给 lessonPrice 时按
 * {@code pricePerHour = lessonPrice / hoursPerLesson} 折算，老调用方零改动不断粮。
 *
 * @author backend-dev
 */
@Data
public class TuitionAccountBo {

    /** 学生 id（雪花，Jackson 由 string 兼容绑定）。开户/改绑必填。 */
    private Long studentId;

    /** 学科字典码（biz_edu_subject；兼容传中文标签，服务端 normalize）。 */
    private String subject;

    /** 账本标签（可选，如「俊羽家」，纯展示）。 */
    private String name;

    /** 时薪（元/小时）。v3 首选入参。 */
    private BigDecimal pricePerHour;

    /** 每节时长（小时）：俊羽 1.5 / 好好 1。缺省 1.00。 */
    private BigDecimal hoursPerLesson;

    /** 🔴 旧契约兼容：每课时单价（元/节）。pricePerHour 为空时按 hoursPerLesson 折算成时薪。 */
    private BigDecimal lessonPrice;

    /** 绑到已有账本（可选）：给了就只建/改绑定，不新建账本（换本、共享账本入口）。 */
    private Long accountId;

    /** 备注（可空）。 */
    private String note;
}
