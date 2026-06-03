package org.dromara.bookadmin.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * /admin/paper/page 分页入参 BO（PRD-B-006 round-2 — 卷库 CRUD admin 通道）。
 *
 * <p>字段命名对齐 {@link org.dromara.book.domain.bo.PaperPageBo}（misikt 风格
 * {@code pageIndex} 不是 pageNum），admin 通道额外支持 {@code status} 筛选。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code name} — 试卷名模糊匹配（LIKE %name%），空 / null 不过滤</li>
 *   <li>{@code subjectId} — 分类前缀匹配（LIKE 'subjectId%'），空 / null 不过滤</li>
 *   <li>{@code status} — '0' 草稿 / '1' 发布 / '2' 软删；null / 空 → admin 看全部状态</li>
 * </ul>
 *
 * @author backend-dev (PRD-B-006 round-2)
 */
@Data
public class AdminPaperPageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码（1-based，misikt 风格） */
    private Integer pageIndex;

    /** 每页数量（misikt 真站 = 10） */
    private Integer pageSize;

    /** 试卷名模糊匹配（LIKE %name%），空串 / null 不过滤 */
    private String name;

    /** 卷分类 id 前缀匹配（biz_paper.subject_id LIKE 'subjectId%'），空串 / null 不过滤 */
    private String subjectId;

    /**
     * 状态筛选（admin 独有，教师端固定过滤 status='1'）。
     *
     * <p>取值 {@code "0"} 草稿 / {@code "1"} 已发布 / {@code "2"} 软删；
     * {@code null} 或空串 → 不过滤（admin 看全部状态，含草稿 + 软删）。
     */
    private String status;
}
