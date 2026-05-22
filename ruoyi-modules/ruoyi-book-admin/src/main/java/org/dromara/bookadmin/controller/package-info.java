/**
 * admin 后台管理 Controller 包（book-admin 工程入口，前缀 /admin/...）。
 *
 * <p>与教师端 {@code org.dromara.book.controller.*} 物理隔离：
 * <ul>
 *   <li>响应 envelope：本包走 RuoYi 标准 {@code R<T>{code:200, msg, data}}，
 *       不被 {@code MisiktEnvelopeAdvice}（限 /teacher/ 前缀）覆盖；</li>
 *   <li>鉴权：走 Sa-Token 标准 {@code @SaCheckPermission("admin:xxx:xxx")} 注解，
 *       权限标识在 sys_menu 表维护，靠 RuoYi 动态菜单加载分发到 FE；</li>
 *   <li>业务复用：注入 admin 自有 {@code IAdminQuestionService} —
 *       <strong>禁注入</strong> {@code org.dromara.book.service.IQuestionService}
 *       （教师端 Service），方法内部不互通。</li>
 * </ul>
 */
package org.dromara.bookadmin.controller;
