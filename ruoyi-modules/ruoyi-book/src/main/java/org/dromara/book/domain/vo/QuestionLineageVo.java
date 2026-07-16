package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题目血缘查询 VO（PRD-C-204）—— {@code GET /teacher/question/{id}/lineage} 返回结构。
 *
 * <p>三种角色（{@link #role}）：
 * <ul>
 *   <li>{@code "variant"} — 该题是变式：{@code mother}=其母题；{@code variants}=同一母题下的全部兄弟（含自己）</li>
 *   <li>{@code "mother"} — 该题是母题（被别的题指为母题）：{@code mother}=该题自己；{@code variants}=其全部子变式</li>
 *   <li>{@code "none"} — 既非变式也无子题：{@code mother}=null；{@code variants}=[]</li>
 * </ul>
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
public class QuestionLineageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色：variant / mother / none */
    private String role;

    /**
     * 母题来源（DB mother_source）—— 血缘家族性质区分依据。
     * {@code "教材配套"} = 典型例题/配套练习对；其余（含 null）= 举一反三母题/变式题。
     * variant 角色取本题（回落母题）的 mother_source；mother 角色取自身。
     */
    private String motherSource;

    /** 母题节点（variant=母题；mother=自己；none=null） */
    private LineageNodeVo mother;

    /** 兄弟/子变式列表（variant=同母全兄弟含自己；mother=全子题；none=[]） */
    private List<LineageNodeVo> variants;
}
