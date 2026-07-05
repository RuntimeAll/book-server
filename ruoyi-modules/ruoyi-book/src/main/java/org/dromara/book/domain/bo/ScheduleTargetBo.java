package org.dromara.book.domain.bo;

import lombok.Data;

/**
 * 对象档案建/改入参（PRD-C-213 /teacher/schedule/target）。
 * targetType='0' 学生用全字段；'1' 班级只用 name/grade/subject/color/profileJson。
 *
 * @author backend-dev
 */
@Data
public class ScheduleTargetBo {
    /** 对象类型：'0' 学生 / '1' 班级 */
    private String targetType;
    private String name;
    private String grade;
    private String subject;
    private String textbook;
    private String parentPhone;
    /** 日历着色（空则色板轮转分配） */
    private String color;
    /** 肖像 JSON（对象/字符串均可，服务端归一化存字符串） */
    private Object profileJson;
}
