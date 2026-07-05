package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 计划课次实体（biz_course_plan_lesson，PRD-C-213）。大纲/细备双精度：大纲态课次无题也合法。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_course_plan_lesson")
public class BizCoursePlanLesson extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属计划 id */
    private Long planId;

    /** 课次序号（第几次课；顺延/排课按此） */
    private Integer lessonSeq;

    /** 课次标题/主题 */
    private String title;

    /** 课次类型：'0' 教学 / '1' 测试 */
    private String lessonType;

    /** 自由标签（吃透课①走这） */
    private String tag;

    /** 素材源 */
    private String sourceRef;

    /** 思维动作（内部字段） */
    private String thinkingAction;

    /** 目标层数（挂课次；与题星级两刻度互不换算） */
    private String layerTarget;

    /** 家长版口语文案 */
    private String parentCopy;

    /** KG 锚点 JSON（biz_subject.id 数组） */
    private String kgNodeIds;

    /** 本课分段配置 JSON（空则继承 plan） */
    private String segTemplate;

    /** 内容态：'0' 大纲态 / '1' 细备中 / '2' 已备好 */
    private String prepState;
}
