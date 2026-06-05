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

    /**
     * U 卡新增 — 创建人 user_id（biz_paper.create_by VARCHAR(64) 存数字字符串）。
     * <p>FE 工作台"我创建的卷"section 调本端点时传当前老师 user_id（字符串）；空串 = 不过滤。
     * <p>注意 biz_paper.create_by 字段类型是 VARCHAR(64)，DB 真实存 admin/admin_id 的字符串形式
     * （V2 ETL CAST(raw_paper.create_user AS CHAR)，参 P 卡 §0.4 沉淀）。
     */
    private String createBy;

    /**
     * 卷库视图范围（scope 分流，前端传）：
     * <ul>
     *   <li>{@code "public"} — 公共卷：按 subject_id 分类树前缀（3001/3003/3004）筛，跨教师可见
     *       （PRD-B-013 已 DROP is_share 死字段，公共卷库语义由分类树决定）</li>
     *   <li>{@code "mine"}   — 我的卷库：WHERE create_by = #{当前登录 userId}（绝不信任前端传的 createBy）</li>
     *   <li>缺省 / 非法值   — 按 "public" 处理（安全默认，绝不暴露他人私卷）</li>
     * </ul>
     */
    private String scope;
}
