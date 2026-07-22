package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 页级审核状态（biz_review_page，PRD-006）。书×源页一行，页级确认（D7）。
 *
 * <p>🔴 无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 * 审计列（reviewedBy/reviewedTime）由 Service 显式 set（LoginHelper.getUserId），不走 MetaObjectHandler。
 *
 * @author backend-dev (PRD-006 批1)
 */
@Data
@TableName("biz_review_page")
public class BizReviewPage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 书 biz_shelf_book.id */
    private Long bookId;

    /** 源书页码 */
    private Integer pageNo;

    /** 0 未审 / 1 已确认 */
    private Integer reviewed;

    /** 确认人 sys_user.id */
    private Long reviewedBy;

    private Date reviewedTime;

    private Date createTime;

    private Date updateTime;
}
