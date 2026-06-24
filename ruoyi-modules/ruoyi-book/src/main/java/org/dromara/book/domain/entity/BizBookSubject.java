package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 教辅目录树实体（biz_book_subject）—— PRD-C-204 B1 录入接口新建。
 *
 * <p>教辅自身的章/节/课时树（level5=课时），区别于 master 知识图谱 biz_subject。
 * id 业务编码 varchar(20)，调用方显式给（IdType.INPUT），录入走 upsert。
 *
 * <p>无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
@TableName("biz_book_subject")
public class BizBookSubject implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点编码 PK（varchar(20)，调用方给） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 归属教辅 biz_book.id */
    private String bookId;

    /** 父节点 id */
    private String parentId;

    /** 节点名（NOT NULL） */
    private String name;

    /** 层级（NOT NULL，level5=课时） */
    private Integer level;

    /** 同层排序 */
    private Integer sort;

    /** 状态 char(1) */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间（DDL DEFAULT CURRENT_TIMESTAMP） */
    private Date createTime;

    /** 更新时间（DDL ON UPDATE CURRENT_TIMESTAMP） */
    private Date updateTime;
}
