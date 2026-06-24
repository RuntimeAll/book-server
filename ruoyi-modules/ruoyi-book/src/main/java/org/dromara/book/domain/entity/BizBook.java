package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 教辅/教材版本实体（biz_book）—— PRD-C-204 B1 录入接口新建。
 *
 * <p>id 为业务编码（varchar(6)），由调用方显式给（IdType.INPUT），录入走 upsert。
 *
 * <p>无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
@TableName("biz_book")
public class BizBook implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 教辅编码 PK（varchar(6)，调用方给） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 学科名 */
    private String subjectName;

    /** 年级 */
    private String grade;

    /** 学期 */
    private String term;

    /** 版本（人教/北师等） */
    private String edition;

    /** 基础版本 */
    private String baseEdition;

    /** 教辅类型 */
    private String bookType;

    /** 系列（必刷题等） */
    private String series;

    /** 学年 */
    private String schoolYear;

    /** 出版社 */
    private String publisher;

    /** 全名 */
    private String fullName;

    /** 封面 URL */
    private String coverUrl;

    /** 状态 char(1) */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间（DDL DEFAULT CURRENT_TIMESTAMP） */
    private Date createTime;
}
