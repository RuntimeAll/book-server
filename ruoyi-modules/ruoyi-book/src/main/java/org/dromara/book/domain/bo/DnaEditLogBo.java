package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DNA 编辑经验层留痕 写入入参 BO（PRD-C-100 B4）。
 *
 * <p>POST /teacher/dna-edit-log 写一行：
 * <ul>
 *   <li>{@code editKind} 必填（dna维 / 图修正）</li>
 *   <li>{@code targetId} / {@code dim} / {@code before} / {@code after} / {@code correctionPrompt} / {@code committed} 可选</li>
 * </ul>
 *
 * <p>🔴 teacher_id 不在 BO 里，永远以登录态 LoginHelper.getUserId() 为准。
 *
 * @author PRD-C-100 B4
 */
@Data
public class DnaEditLogBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母题/变式 id 或 thread_id（可选） */
    private String targetId;

    /** 编辑类型（dna维 / 图修正） */
    private String editKind;

    /** 改的哪一维（dna维时，可选） */
    private String dim;

    /** 改前值（可选） */
    private String before;

    /** 改后值（可选） */
    private String after;

    /** 图修正提示词（可选） */
    private String correctionPrompt;

    /** 是否入库（可选，缺省 false） */
    private Boolean committed;
}
