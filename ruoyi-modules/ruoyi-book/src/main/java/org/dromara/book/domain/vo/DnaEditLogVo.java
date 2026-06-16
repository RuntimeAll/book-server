package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * DNA 编辑经验层留痕响应 VO（PRD-C-100 B4，调试用 list）。
 *
 * <p>GET /teacher/dna-edit-log/list 返该 teacher 最近 N 行。
 *
 * @author PRD-C-100 B4
 */
@Data
public class DnaEditLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 留痕 id */
    private Long id;

    /** 母题/变式 id 或 thread_id */
    private String targetId;

    /** 编辑类型（dna维 / 图修正） */
    private String editKind;

    /** 改的哪一维 */
    private String dim;

    /** 改前值 */
    private String beforeVal;

    /** 改后值 */
    private String afterVal;

    /** 图修正提示词 */
    private String correctionPrompt;

    /** 是否入库（1=已入库） */
    private Integer committed;

    /** 创建时间 */
    private Date createdAt;
}
