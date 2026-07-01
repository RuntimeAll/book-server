package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * /teacher/question/lazyTree 响应嵌套节点 VO。
 *
 * <p>字段命名按 misikt 风格（驼峰）+ 本工程归一化口径：
 * <ul>
 *   <li>name + title 双字段冗余 — 派活 prompt 要求 name，FE TS interface 用 title，全返兼容</li>
 *   <li>createTime 归一化 BIGINT ms timestamp（不复刻 misikt STRING/BIGINT 漂移）</li>
 *   <li>nodeDataSum 固定 null（misikt 抓包总是 null — 该字段在 misikt 实际未启用）</li>
 *   <li>key / value 复用 id（misikt 给 Ant Tree 用，FE 不依赖也无害）</li>
 * </ul>
 *
 * <p>🔴 PRD-B-013 减法：删除知识点配图/视频/共享标记 3 字段（biz_subject 同步 DROP）。
 *
 * @author backend-dev
 */
@Data
public class SubjectNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String parentId;

    /**
     * 节点名称（本工程主字段，对齐子 PRD §4.5 端点契约）
     */
    private String name;

    /**
     * 节点名称（同 name，FE TS interface 真实用此字段 — 兼容字段）
     */
    private String title;

    /**
     * 层级 1-5
     */
    private Integer level;

    /**
     * 同层排序
     */
    private Integer sort;

    /**
     * 创建时间（毫秒 timestamp，归一化 BIGINT ms）
     */
    private Long createTime;

    /**
     * 是否有子节点（构建树时算）
     */
    private Boolean hasChildren;

    /**
     * key（Ant Tree 兼容字段，复用 id）
     */
    private String key;

    /**
     * value（Ant Tree 兼容字段，复用 id）
     */
    private String value;

    /**
     * 节点数据汇总（misikt 抓包恒 null，本工程占位返 null）
     */
    private Long nodeDataSum;

    // ── KG 结构化维度（2026-07-01 字典化，仅 level=1 教材根有值；前端按码 + useDictStore 渲染，不再解析 title）──
    /** 学科 dict biz_edu_subject */
    private Integer subject;
    /** 学段 dict biz_edu_stage */
    private Integer stage;
    /** 年级 dict biz_edu_grade */
    private Integer grade;
    /** 册 dict biz_edu_volume */
    private Integer volume;
    /** 版本基名 dict biz_edu_edition */
    private Integer edition;
    /** 版本年份 2024/2012/0 */
    private Integer editionYear;

    /**
     * 子节点（嵌套树）
     */
    private List<SubjectNodeVo> children;
}
