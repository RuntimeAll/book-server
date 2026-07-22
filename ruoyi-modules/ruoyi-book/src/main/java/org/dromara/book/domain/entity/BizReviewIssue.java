package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 录题问题登记表（biz_review_issue，PRD-006 D2）。跨书沉淀，转录改进原料。
 *
 * <p>🔴 无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 * createBy 由 Service 显式 set（LoginHelper.getUserId），不走 MetaObjectHandler；
 * createTime/updateTime 由 Service 显式 set，避免依赖框架填充。
 *
 * @author backend-dev (PRD-006 批1)
 */
@Data
@TableName("biz_review_issue")
public class BizReviewIssue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 书 biz_shelf_book.id */
    private Long bookId;

    /** 题 biz_question.id（可空：整页问题） */
    private Long questionId;

    /** 源书页码 */
    private Integer sourcePage;

    /** 问题类型 */
    private String issueType;

    /** 问题描述 */
    private String description;

    /** 待处理 / 已改 / 搁置 */
    private String status;

    /** 登记人 sys_user.id */
    private Long createBy;

    private Date createTime;

    private Date updateTime;
}
