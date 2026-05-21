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
 *   <li>isShare 归一化 INT 0/1（不复刻 misikt STRING/INT 漂移）</li>
 *   <li>createTime 归一化 BIGINT ms timestamp（不复刻 misikt STRING/BIGINT 漂移）</li>
 *   <li>nodeDataSum 固定 null（misikt 抓包总是 null — 该字段在 misikt 实际未启用）</li>
 *   <li>key / value 复用 id（misikt 给 Ant Tree 用，FE 不依赖也无害）</li>
 * </ul>
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
     * 知识点配图（仅叶子）
     */
    private String knowledgeImg;

    /**
     * 知识点微课视频 URL（仅叶子）
     */
    private String knowledgeVideo;

    /**
     * 是否共享 0/1（归一化 INT）
     */
    private Integer isShare;

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

    /**
     * 子节点（嵌套树）
     */
    private List<SubjectNodeVo> children;
}
