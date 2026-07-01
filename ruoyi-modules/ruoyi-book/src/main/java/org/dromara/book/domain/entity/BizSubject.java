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
     * 状态 '0' 正常 / '1' 停用
     */
    private String status;

    /**
     * 个人题库（我的题库）目录是否展示 '1' 展示 / '0' 隐藏（V21，全局生效，公共题库页不受影响）
     */
    private String mineVisible;

    // ── KG 结构化维度（2026-07-01 枚举字典化，仅 level=1 教材根有值；解析 name 回填）──
    /** 学科 dict biz_edu_subject（1数学 2科学） */
    private Integer subject;

    /** 学段 dict biz_edu_stage（1小学 2初中 3高中） */
    private Integer stage;

    /** 年级 dict biz_edu_grade（1一年级…9九年级 10高一…） */
    private Integer grade;

    /** 册 dict biz_edu_volume（1上册 2下册） */
    private Integer volume;

    /** 版本基名 dict biz_edu_edition（1浙教 2人教，不含年份） */
    private Integer edition;

    /** 版本年份（2024/2012/0） */
    private Integer editionYear;

    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private String remark;
}
