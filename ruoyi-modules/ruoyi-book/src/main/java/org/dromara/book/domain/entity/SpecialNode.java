package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 专项目录节点实体（复用 biz_shelf_node，PRD-003 C 位自建薄实体）。
 *
 * <p>专项节点树两层语义：{@code node_type='sec'}（区块）→ {@code node_type='tier'}（难度档）→ 题（item）。
 * tier 可选；题（item）可直挂 sec 也可挂 tier。tier 级的 star/label/gap 存 {@link #metaJson}。
 *
 * <p>本表无 create_by/update_by/create_dept 列（仅 create_time/update_time），故不继承 BaseEntity，
 * 时间列走 DB DEFAULT CURRENT_TIMESTAMP（insert 不显式赋值）。
 *
 * @author codeplace-C PRD-003
 */
@Data
@TableName("biz_shelf_node")
public class SpecialNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 雪花主键（应用生成） */
    @TableId(value = "id")
    private Long id;

    /** 所属专项书 id */
    private Long bookId;

    /** 父节点 id（NULL=根层区块 sec） */
    private Long parentId;

    /** 同层排序 */
    private Integer seq;

    /** 节点类型：'sec' 区块 / 'tier' 难度档（自由值不设枚举闸） */
    private String nodeType;

    /** 节点名（卷面可见，禁内部词——只写干净知识点名） */
    private String name;

    /** 可选 KG 锚（仅标签，与树结构解耦） */
    private Long kpId;

    /** 节点元数据 JSON（tier 级 {star,label,gap} 等） */
    private String metaJson;

    private Date createTime;

    private Date updateTime;
}
