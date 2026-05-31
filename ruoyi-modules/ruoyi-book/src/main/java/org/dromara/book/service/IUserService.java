package org.dromara.book.service;

import org.dromara.book.domain.bo.RegisterTeacherBo;
import org.dromara.book.domain.bo.UpdateProfileBo;
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

    /**
     * 注册老师账号（U 卡 段⑧）。
     *
     * <p>逻辑：
     * <ol>
     *   <li>userName 重复检查（exists）— 已存在抛 ServiceException</li>
     *   <li>BCrypt 哈希密码（hutool BCrypt.hashpw）</li>
     *   <li>INSERT sys_user：dept_id=103 / user_type=sys_user / tenant_id=000000 / status='0'</li>
     *   <li>反查 sys_role role_key='teacher' 拿 roleId（避免硬编码 ID）</li>
     *   <li>INSERT sys_user_role 绑 teacher 角色</li>
     * </ol>
     *
     * @param bo 注册入参（userName / password / nickName）
     * @return 新建的 user_id（MP 雪花注入）
     * @throws org.dromara.common.core.exception.ServiceException 用户名重复 / teacher 角色未配置
     */
    Long register(RegisterTeacherBo bo);

    /**
     * 更新当前登录教师的个人资料（PRD-002 — POST /teacher/user/update）。
     *
     * <p>userId 从登录态 {@link org.dromara.common.satoken.utils.LoginHelper#getUserId()} 取
     * （Controller 传入），不从 body 收 —— 防越权改他人。
     *
     * <p>写 sys_user 四字段：nick_name(←realName) / sex / grade / school。
     *
     * @param userId 当前登录用户 ID（Sa-Token 解出）
     * @param bo     更新入参（realName / sex / grade / school）
     */
    void updateProfile(Long userId, UpdateProfileBo bo);
}
