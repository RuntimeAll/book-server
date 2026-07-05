package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 备课包实体（biz_prep_pack，PRD-C-213）。1:1 course_plan_lesson 或散课 session（二选一）。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_prep_pack")
public class BizPrepPack extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 绑定课次 id（与 session_id 二选一） */
    private Long planLessonId;

    /** 绑定散课场次 id（与 plan_lesson_id 二选一） */
    private Long sessionId;

    /** 分段内容 JSON：[{name,style,question_ids:[str],rules,note}] */
    private String segs;

    /** 产物 JSON：[{seg,file,pages}] */
    private String artifacts;

    /** 状态：'0' 装配中 / '1' 已生成 / '2' 已备好 */
    private String status;
}
