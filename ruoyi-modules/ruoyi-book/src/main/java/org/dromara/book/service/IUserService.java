package org.dromara.book.service;

import org.dromara.book.domain.vo.CurrentUserVo;

/**
 * 当前用户 Service。
 *
 * @author backend-dev
 */
public interface IUserService {

    /**
     * 拉当前登录教师信息（misikt /teacher/user/current 对齐）。
     *
     * @param userId Sa-Token 解出的当前用户 ID（LoginHelper.getUserId()）
     * @return CurrentUserVo（教师字段映射；未复刻字段返 null）
     */
    CurrentUserVo getCurrent(Long userId);
}
