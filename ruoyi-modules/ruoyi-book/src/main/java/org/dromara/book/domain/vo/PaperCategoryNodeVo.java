package org.dromara.book.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * /teacher/exam/paper/lazyTree 响应嵌套节点 VO（D 卡卷库视觉级还原）。
 *
 * <p>字段命名严格按 misikt 真响应（A5-paper-lazyTree.json + lazyTree-response.json）字节级对齐：
 * <ul>
 *   <li>{@code title} — 节点名（不是 name）</li>
 *   <li>{@code hasChildren} — boolean，叶节点 false 显式带；非叶子也显式带 true</li>
 *   <li>{@code key} / {@code value} — 跟 id 同值（element-plus tree 用）</li>
 *   <li>{@code level} — null（misikt 真响应固定 null）</li>
 *   <li>{@code nodeDataSum} — null（misikt 真响应固定 null）</li>
 *   <li>{@code children} — 递归，叶节点不返该字段（@JsonInclude NON_NULL 排除）</li>
 * </ul>
 *
 * <p>字段顺序按 misikt 真响应：id / parentId / nodeDataSum / children / hasChildren / title /
 * key / value / level / sort（Lombok @Data 不保字段顺序但 Jackson 默认按声明顺序 —
 * 用 @JsonPropertyOrder 兜底）。
 *
 * <p>🔴 PRD-B-013 减法：删除共享标记字段（biz_paper_category 此列同步 DROP）。
 *
 * @author backend-dev
 */
@Data
@com.fasterxml.jackson.annotation.JsonPropertyOrder({
    "id", "parentId", "nodeDataSum", "children", "hasChildren",
    "title", "key", "value", "level", "sort"
})
public class PaperCategoryNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String parentId;

    /** misikt 真响应恒 null，占位返 null */
    private Long nodeDataSum;

    /** 子节点（叶节点不返该字段 — NON_NULL 排除） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PaperCategoryNodeVo> children;

    /** 是否有子节点；叶节点 false / 非叶子 true（misikt 都显式带） */
    private Boolean hasChildren;

    /** 节点名（misikt 用 title 字段） */
    private String title;

    /** element-plus tree node-key，复用 id */
    private String key;

    /** element-plus tree value，复用 id */
    private String value;

    /** misikt 真响应恒 null，占位返 null */
    private Integer level;

    /** 同层排序 */
    private Integer sort;

    // ── 结构化维度（2026-07-01 字典化，前端按码 + useDictStore 渲染，不再解析 title）──
    /** 学科 dict biz_edu_subject */
    private Integer subject;
    /** 学段 dict biz_edu_stage */
    private Integer stage;
    /** 年级 dict biz_edu_grade（中考/资料库=null） */
    private Integer grade;
    /** 册 dict biz_edu_volume（九年级/中考=null） */
    private Integer volume;
    /** 卷型 dict biz_paper_type */
    private Integer paperType;
    /** 节点类型 root/grade/ptype/exam/chapter/year/misc */
    private String nodeKind;
}
