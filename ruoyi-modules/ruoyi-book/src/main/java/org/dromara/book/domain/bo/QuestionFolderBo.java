package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目收藏夹 CRUD 入参 BO（PRD-A-005 收尾 B-收藏夹 CRUD）。
 *
 * <p>三个写接口共用：
 * <ul>
 *   <li>新建：用 {@code name}（必填）+ {@code pid}（可选，缺省 0）；user_id 一律取 LoginHelper.getUserId()</li>
 *   <li>改名：用 {@code id} + {@code name}（仅名称可改）</li>
 *   <li>删除：用 {@code id}</li>
 * </ul>
 *
 * <p>🔴 绝不信任前端传的归属字段，user_id 永远以登录态为准。
 *
 * @author backend-dev
 */
@Data
public class QuestionFolderBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 收藏夹 id（改名 / 删除用） */
    private Long id;

    /** 收藏夹名称（新建 / 改名用） */
    private String name;

    /** 父夹 id（新建可选，缺省 0=根） */
    private Long pid;
}
