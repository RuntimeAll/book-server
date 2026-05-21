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
 *   <li>{@code isShare} — STRING '0'/'1'（注意不是 INT，跟章节树 SubjectNodeVo 不一样）</li>
 *   <li>{@code hasChildren} — boolean，叶节点 false 显式带；非叶子也显式带 true</li>
 *   <li>{@code key} / {@code value} — 跟 id 同值（element-plus tree 用）</li>
 *   <li>{@code level} — null（misikt 真响应固定 null）</li>
 *   <li>{@code nodeDataSum} — null（misikt 真响应固定 null）</li>
 *   <li>{@code children} — 递归，叶节点不返该字段（@JsonInclude NON_NULL 排除）</li>
 * </ul>
 *
 * <p>字段顺序按 misikt 真响应：id / parentId / nodeDataSum / children / hasChildren / title /
 * isShare / key / value / level / sort（Lombok @Data 不保字段顺序但 Jackson 默认按声明顺序 —
 * 用 @JsonPropertyOrder 兜底）。
 *
 * @author backend-dev
 */
@Data
@com.fasterxml.jackson.annotation.JsonPropertyOrder({
    "id", "parentId", "nodeDataSum", "children", "hasChildren",
    "title", "isShare", "key", "value", "level", "sort"
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

    /** 是否共享 STRING '0'/'1'（misikt 真响应字符串口径，跟 SubjectNodeVo INT 不同） */
    private String isShare;

    /** element-plus tree node-key，复用 id */
    private String key;

    /** element-plus tree value，复用 id */
    private String value;

    /** misikt 真响应恒 null，占位返 null */
    private Integer level;

    /** 同层排序 */
    private Integer sort;
}
