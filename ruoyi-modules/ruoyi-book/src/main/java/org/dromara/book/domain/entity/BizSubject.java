package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 章节-知识点节点实体（biz_subject）
 *
 * <p>5 层树：1=学科 / 2=教材 / 3=章 / 4=节 / 5=知识点（叶子）。
 * id 每 3 位一层（例如 3071 / 3071001 / 3071001001）。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_subject")
public class BizSubject implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 层级数字编码 PK（每 3 位一层）
     */
    @TableId(value = "id")
    private String id;

    /**
     * 父节点 ID（根节点的 parent_id 在数据集中是 "1" / null）
     */
    private String parentId;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 层级：1 学科 / 2 教材 / 3 章 / 4 节 / 5 知识点
     */
    private Integer level;

    /**
     * 同层排序
     */
    private Integer sort;

    /**
     * 知识点配图（仅叶子）
     */
    private String knowledgeImg;

    /**
     * 知识点微课视频 URL（仅叶子）
     */
    private String knowledgeVideo;

    /**
     * 是否共享：0 / 1（misikt 抓包出现 STRING / INT 漂移，DB 用 TINYINT 归一化）
     */
    private Integer isShare;

    /**
     * 状态 '0' 正常 / '1' 停用
     */
    private String status;

    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private String remark;
}
