package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 书架·目录节点树（biz_shelf_node，PRD-002）。每本书自持目录，不做通性抽离；kp_id 仅为可选标签，与树结构完全解耦（D8）。
 *
 * <p>本表无 BaseEntity 审计四列（DDL 只有 create_time/update_time），故不继承 BaseEntity。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_shelf_node")
public class BizShelfNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long bookId;

    /** NULL = 根层 */
    private Long parentId;

    /** 同层排序 */
    private Integer seq;

    /** chapter章/lecture讲/qtype_group题型组/tier难度档/sec区块/…自由值不设枚举闸 */
    private String nodeType;

    /** 节点名（卷面可见，禁内部词） */
    private String name;

    /** 可选 KG 锚（仅标签，与树结构完全解耦，D8） */
    private Long kpId;

    private String metaJson;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
