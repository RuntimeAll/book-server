package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.RegisterTeacherBo;
import org.dromara.book.domain.vo.CurrentUserVo;
import org.dromara.book.service.IUserService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * POST /teacher/user/register — 老师自助注册（U 卡 段⑧）。
     *
     * <p>用 {@code @SaIgnore} 让 Sa-Token 全局拦截器跳过本方法（注册时未登录是常态）。
     *
     * <p>入参：userName(4-20) / password(6-20) / nickName?(≤30)
     * <p>出参：{"userId": <Long>}
     *
     * <p>错误：
     * <ul>
     *   <li>用户名已被占 → 400 业务异常 "用户名 X 已被注册"</li>
     *   <li>校验失败 → 400 含字段提示（@Valid 抛 MethodArgumentNotValidException）</li>
     *   <li>teacher 角色未配置 → 500 "教师角色未配置..."</li>
     * </ul>
     */
    @SaIgnore
    @PostMapping("/register")
    public R<Map<String, Object>> register(@Valid @RequestBody RegisterTeacherBo bo) {
        Long userId = userService.register(bo);
        Map<String, Object> resp = new HashMap<>(2);
        resp.put("userId", userId);
        resp.put("userName", bo.getUserName());
        return R.ok("注册成功", resp);
    }
}
