package org.dromara.bookadmin.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * /admin/paper/edit 新建 / 编辑统一入参 BO（PRD-B-006 round-2 — 卷库 CRUD admin 通道）。
 *
 * <p>分支（与 AdminQuestionEditBo 同语义）：
 * <ul>
 *   <li>{@code id == null} → 新建：INSERT biz_paper (status='0' 草稿，paper_type=1 手工)</li>
 *   <li>{@code id != null} → 编辑：UPDATE biz_paper（status 不动，要发布走 publish 端点）</li>
 * </ul>
 *
 * <p>FE 契约对齐 {@code PaperForm} TypeScript 类型（admin/paper/types.ts）：
 * <ul>
 *   <li>id 可空 = 新建</li>
 *   <li>name 必填</li>
 *   <li>subjectId / paperCategoryId / suggestTime / examYear / remark 可选</li>
 * </ul>
 *
 * @author backend-dev (PRD-B-006 round-2)
 */
@Data
public class AdminPaperEditBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 试卷 id（null = 新建，非 null = 编辑 — biz_paper.id）
     */
    private Long id;

    /**
     * 试卷名（必填）
     */
    private String name;

    /**
     * 卷分类编码（biz_paper.subject_id，可选）
     */
    private String subjectId;

    /**
     * 卷分类目录 id（biz_paper.paper_category_id，可选）
     */
    private String paperCategoryId;

    /**
     * 目录名（biz_paper.directory_name，可选）
     */
    private String directoryName;

    /**
     * 建议时长（分钟，biz_paper.suggest_time，可选）
     */
    private Integer suggestTime;

    /**
     * 考试年份（biz_paper.exam_year，可选，如 "2025"）
     */
    private String examYear;

    /**
     * 备注（biz_paper.remark，可选）
     */
    private String remark;
}
