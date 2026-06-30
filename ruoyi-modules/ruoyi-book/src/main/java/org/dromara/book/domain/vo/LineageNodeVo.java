package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 血缘节点轻量 VO（PRD-C-204 血缘查询）—— 母题 / 兄弟变式列表元素。
 *
 * <p>只携带渲染血缘卡片所需的最小字段，不带题面长文本 / 富文本块 / DNA，
 * 避免血缘端点把整棵题树的重字段全拉回。
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
public class LineageNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目 id（雪花 Long；全局 BigNumberSerializer 大于 JS 安全整数时序列化为 string，防精度丢）。
     */
    private Long id;

    /** 题干前 60 字纯文本摘要（剥 HTML 标签 + 折叠空白） */
    private String stemBrief;

    /** 题型 1选择/4填空/5解答/6作图 */
    private Integer questionType;

    /** 难度 1-4 星 */
    private Integer difficult;

    /** 变式关系（数值变式/情境变式/结构变式/同源；母题自身为 null） */
    private String variantRelation;
}
