package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 试卷分类树节点实体（biz_paper_category）— D 卡卷库视觉级还原。
 *
 * <p>独立于 biz_subject 章节树，承担"卷库左侧目录"。4-15 位数字编码 PK，
 * misikt 真响应 lazyTree 共 97 节点（3 根：3001 公共试卷 / 3003 资料库 / 3004 专题卷库）。
 *
 * <p>seed 落库后 D 卡 V0.5 不再写入（先 read-only），未来 V1.5 后台管理才开 CRUD。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_paper_category")
public class BizPaperCategory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类 id（4-15 位数字编码字符串） */
    @TableId(value = "id")
    private String id;

    /**
     * 父节点 id（根节点 '0' / 6 行 deprecated 标记 '-deprecated'，service 层 WHERE parent_id NOT LIKE '-%'）
     */
    private String parentId;

    /** 节点名 */
    private String name;

    /** 同层排序（asc） */
    private Integer sort;

    // ── 结构化维度（2026-07-01 枚举字典化，语义下沉每节点；解析 name 回填）──
    /** 学科 dict biz_edu_subject */
    private Integer subject;

    /** 学段 dict biz_edu_stage */
    private Integer stage;

    /** 年级 dict biz_edu_grade（中考/资料库=null） */
    private Integer grade;

    /** 册 dict biz_edu_volume（九年级/中考=null） */
    private Integer volume;

    /** 卷型 dict biz_paper_type（1单元 2月考 3期中 4期末） */
    private Integer paperType;

    /** 节点类型 root/grade/ptype/exam/chapter/year/misc */
    private String nodeKind;
}
