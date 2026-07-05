package org.dromara.book.domain.bo;

import lombok.Data;

/** 排课/冲突检测单条 item（PRD-C-213）。 */
@Data
public class SessionItemBo {
    /** 上课日期 yyyy-MM-dd */
    private String date;
    /** 开始时间 HH:mm */
    private String start;
    /** 结束时间 HH:mm */
    private String end;
    /** 显式绑定课次 id（可空；autoBind 时留空由服务端按序绑） */
    private Long planLessonId;
    /** 场次类型：'1' 正课 / '2' 测试 / '3' 外部占位 */
    private String sessionType;
    /** 外部占位标题 */
    private String externalTitle;
    private String note;
}
