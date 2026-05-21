package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.CurrentUserVo;
import org.dromara.book.service.IUserService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师当前用户 Controller。
 *
 * <p>路径前缀对齐 misikt 真实端点 /teacher/user/...
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * POST /teacher/user/current — 拉当前登录教师信息。
     *
     * <p>鉴权：Sa-Token JWT；Service 走 LoginHelper.getUserId() 解出。
     */
    @SaCheckLogin
    @PostMapping("/current")
    public R<CurrentUserVo> current() {
        Long userId = LoginHelper.getUserId();
        CurrentUserVo vo = userService.getCurrent(userId);
        return R.ok(vo);
    }
}
