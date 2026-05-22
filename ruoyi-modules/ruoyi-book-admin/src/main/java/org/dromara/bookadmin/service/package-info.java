/**
 * admin 后台管理 Service 包（H1 卡 V-1~V-6 写端点统一事务入口）。
 *
 * <p>本模块（{@code ruoyi-book-admin}）与教师端 {@code ruoyi-book} 物理隔离：
 * <ul>
 *   <li>实体 / Mapper / VO 共享（依赖 {@code ruoyi-book}），DDL 单源；</li>
 *   <li>Service 方法独立维护，admin 改 page 不影响 teacher，反之亦然；</li>
 *   <li>禁调 {@code org.dromara.book.service.*} 任何方法，必须走 Mapper 自查。</li>
 * </ul>
 */
package org.dromara.bookadmin.service;
