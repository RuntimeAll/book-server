package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 排课场次实体（biz_schedule_session，PRD-C-213）。老师撞场口径按 create_by。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_schedule_session")
public class BizScheduleSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对象类型：'0' 学生 / '1' 班级 */
    private String targetType;

    /** 对象 id */
    private Long targetId;

    /** 绑定计划 id（可空） */
    private Long planId;

    /** 绑定课次 id（可空；autoBind 按 lesson_seq 绑） */
    private Long planLessonId;

    /** 上课日期 */
    private LocalDate sessionDate;

    /** 开始时间（HH:mm 或 HH:mm:ss 文本） */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 场次类型：'1' 正课 / '2' 测试 / '3' 外部占位 */
    private String sessionType;

    /** 学科（字典 biz_edu_subject；NULL=展示时兜底 计划→对象） */
    private String subject;

    /** 状态：'0' 已排 / '1' 已上 / '2' 请假 / '3' 取消 */
    private String sessionStatus;

    /**
     * 结算态（PRD-015 D4）：'0' 未结 / '1' 已结 / '2' 已冲正。
     *
     * <p>🔴 只由结算链写：一键结算置 '1'，请假/取消冲正置 '2'（见 SettlementService）；
     * 排课/改期/归档联动取消一律不动本列（不自动扣费，D4 只提醒语义）。
     */
    private String settleStatus;

    /** 场次备课态：'0' 未备 / '1' 备课中 / '2' 已备好 */
    private String prepStatus;

    /** 内容锁定：'0' 否 / '1' 是（顺延跳过锁定场次） */
    private String lessonLocked;

    /** 外部占位标题 */
    private String externalTitle;

    /** 备注 */
    private String note;

    /**
     * 这节课实际讲了什么（PRD-018 ③，≤200 字）。
     *
     * <p>写入口两处：PUT /session/{id}（改场次）与 POST /settle 的 item.content（结算时顺手记）。
     * 台账「内容」列取值链 = 本列 → 课次标题 → 「正课」（{@code TuitionAccountService.contentOf}）。
     */
    private String content;
}
