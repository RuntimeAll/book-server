/**
 * book-admin 后台业务出参 VO 包（与 ruoyi-book 共享 VO 解耦，admin 专属 VO 落此处）。
 *
 * <p>命名约定：admin 独有 VO 以 {@code Admin*Vo} 或语义清晰的纯名（如
 * {@code TagWithCoCountVo}）命名；admin 可复用 {@code org.dromara.book.domain.vo.*}
 * 共享 VO（DDL 单源）— 此包只放教师端没有、admin 端特有的出参结构。
 */
package org.dromara.bookadmin.domain.vo;
