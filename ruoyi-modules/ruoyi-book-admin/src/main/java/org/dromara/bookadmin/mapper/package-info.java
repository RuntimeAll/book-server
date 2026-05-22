/**
 * book-admin 模块 admin 专属 Mapper 包（H1 卡段② BE 波 2a 新增）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：admin 模块**共享** {@code ruoyi-book} 的
 * entity / VO / BO（DDL 单源）+ 大部分 Mapper bean（如 {@code BizQuestionMapper}），
 * 但当 admin 需要的 SQL 在 teacher mapper 里不存在时，admin <strong>不得</strong>
 * 改动 {@code ruoyi-book} 模块的 mapper / xml（另一线程在改用户端 / 教师端），
 * 而是在本包下新建 admin 自有 Mapper + 自有 mapper.xml。
 *
 * <p>当前包内 Mapper：
 * <ul>
 *   <li>{@link org.dromara.bookadmin.mapper.AdminPaperQuestionRefMapper} —
 *       biz_paper_question 引用计数（V-2 软删校验用），ruoyi-book 现有
 *       {@code BizPaperQuestionMapper} 只有 selectPapersByQuestionId 没有 count 方法。</li>
 * </ul>
 *
 * <p>mapper.xml 落点：{@code ruoyi-book-admin/src/main/resources/mapper/bookadmin/}
 *（避开 ruoyi-book 的 {@code mapper/book/} namespace 冲突）。
 *
 * @author backend-dev
 */
package org.dromara.bookadmin.mapper;
