package org.dromara.bookadmin.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.book.domain.bo.QuestionPageBo;

import java.io.Serial;
import java.util.List;

/**
 * /admin/question/page 入参 BO（H1 卡 Bug2 补丁 — admin 端独有多选 tag 筛选）。
 *
 * <p>继承 {@link QuestionPageBo}（subjectId / questionType / difficult / keyWord /
 * notUsedQuestion / pageIndex / pageSize 等共有字段不重复写），<strong>仅扩展</strong>
 * admin 列表页独有的多选标签筛选字段 {@code tagIds}。
 *
 * <p>语义（PRD §3.X — H1 Bug2 补丁）：
 * <ul>
 *   <li>{@code tagIds == null} 或 {@code tagIds.isEmpty()} → 不过滤（与未传等效）</li>
 *   <li>{@code tagIds.size() ≥ 1} → service 层 buildAdminPageWrapper 加
 *       {@code WHERE id IN (SELECT DISTINCT question_id FROM biz_question_free_tag WHERE tag_id IN (...))}
 *       — <strong>OR 语义</strong>（多选标签是"任一命中即返"，不是 AND 全命中）</li>
 * </ul>
 *
 * <p>为啥 admin 独有：教师端列表当前不需要 tag 多选筛选（misikt 真实抓包 questionPage 无此入参），
 * admin 后台批量管理需快速按 tag 圈题，所以 admin 自有 BO 扩展。
 *
 * @author backend-dev (H1 卡 Bug2 补丁)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminQuestionPageBo extends QuestionPageBo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签 ID 多选筛选（biz_free_tag.id 列表）。
     *
     * <p>OR 语义：命中任一 tag 的题目即返。null / 空数组等效"不过滤"。
     *
     * <p>service 层走 {@code inSql} 子查询拼接，tagIds 是 Long 列表，
     * {@code stream.map(String::valueOf)} 安全（不会 SQL 注入）。
     */
    private List<Long> tagIds;
}
