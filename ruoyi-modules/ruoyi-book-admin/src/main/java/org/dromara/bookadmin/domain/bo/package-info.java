/**
 * ruoyi-book-admin 模块自有 BO 包（admin 端入参对象，与教师端 BO 物理隔离）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：admin 写场景的入参 BO 必须落本包，
 * <strong>禁与 teacher 共享</strong>（防 admin 加字段污染 teacher 端契约）。
 * 共享只允许 {@code ruoyi-book.domain.entity}（DDL 单源）+
 * {@code ruoyi-book.domain.vo}（出参共享）+ Mapper（数据访问层共享）。
 *
 * @author backend-dev (H1 卡段② BE 波 2b)
 */
package org.dromara.bookadmin.domain.bo;
