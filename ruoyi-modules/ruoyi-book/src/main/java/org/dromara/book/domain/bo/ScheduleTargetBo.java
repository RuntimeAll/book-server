package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 对象档案建/改入参（PRD-C-213 /teacher/schedule/target；R1a 建模拍板后为新字段口径）。
 * targetType='0' 学生用全字段；'1' 班级不用 parentPhone。
 *
 * @author backend-dev
 */
@Data
public class ScheduleTargetBo {
    /** 对象类型：'0' 学生 / '1' 班级 */
    private String targetType;
    private String name;
    /** 年级 1-12（字典 biz_edu_grade；= gradeYear 学年就读年级） */
    private Integer gradeNo;
    /** gradeNo 生效学年起始年（如 2026；暑期录入「升四」= gradeNo 4 + gradeYear 2026） */
    private Integer gradeYear;
    /** 教材版本字典码（biz_edu_edition：1浙教/2人教/3北师大/4苏教；兼容中文标签入参，服务端归一化） */
    private String textbookEdition;
    /** 学科字典码（biz_edu_subject：1数学/2科学/3语文/4英语；兼容中文标签入参，服务端归一化） */
    private String subject;
    private String parentPhone;
    /** 日历着色（空则色板轮转分配） */
    private String color;
    /** 肖像 JSON（对象/字符串均可，服务端归一化存字符串） */
    private Object profileJson;
}
