package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 课程计划实体（biz_course_plan，PRD-C-213）。
 *
 * <p>🔴 无 total_lessons 列（= count(lessons) 实时聚合）。R1a·S1 起计划有归属列
 * target_type+target_id（建计划必传）；场次绑定关系仍活在 session.plan_id。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_course_plan")
public class BizCoursePlan extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 计划名称 */
    private String name;

    /** 适配对象类型：'0' 学生 / '1' 班级 */
    private String targetType;

    /** S1 计划归属对象 id（biz_student.id 或 biz_class.id，与 targetType 联合；建计划必传） */
    private Long targetId;

    /** 期段（字典：暑假/上学期/寒假/下学期） */
    private String termTag;

    /** 学科（字典 biz_edu_subject；一计划一科，2026-07-11 学科归位课程安排层） */
    private String subject;

    /** 年份 */
    private Integer year;

    /** 素材池说明 */
    private String materialNote;

    /** 默认分段模板 JSON */
    private String defaultSegTemplate;

    /** 计划级默认卷位模板 JSON（PRD-B-101；lesson.paper_slots 空则继承）。 */
    private String defaultPaperSlots;

    /** 状态：'0' 草稿 / '1' 启用 / '2' 归档 */
    private String status;
}
