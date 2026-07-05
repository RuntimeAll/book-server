package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 课后回收实体（biz_session_review，PRD-C-213）。重复提交=覆盖并把上一版整体快照进 prev_json。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_session_review")
public class BizSessionReview extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 场次 id（uniq） */
    private Long sessionId;

    /** 逐题结果 JSON */
    private String itemResults;

    /** 老师备注 */
    private String teacherNote;

    /** 生成的家长反馈消息 */
    private String parentMsg;

    /** 写回 profile 的 error_signals JSON（by=system,status=pending） */
    private String portraitDelta;

    /** 上一版整体快照 JSON */
    private String prevJson;

    /** 版本号（每次覆盖 +1） */
    private Integer version;
}
