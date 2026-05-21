package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * /teacher/exam/paper/page 入参 BO（D 卡卷库视觉级还原）。
 *
 * <p>字段命名严格对齐 misikt 抓包（A6-paper-page.json）：
 * <ul>
 *   <li>{@code pageIndex} 不是 pageNum（misikt 风格）</li>
 *   <li>{@code subjectId} 走 prefix-match：{@code WHERE subject_id LIKE 'subjectId%'}</li>
 *   <li>{@code name} 走题目名 LIKE %name%</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
public class PaperPageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码（1-based，不是 pageNum！— misikt 风格） */
    private Integer pageIndex;

    /** 每页数量（misikt 真站 = 10） */
    private Integer pageSize;

    /** 试卷名模糊匹配（LIKE %name%），空串 = 不过滤 */
    private String name;

    /** 试卷分类 id，走 prefix-match（biz_paper.subject_id LIKE 'subjectId%'）；空串 = 不过滤 */
    private String subjectId;
}
