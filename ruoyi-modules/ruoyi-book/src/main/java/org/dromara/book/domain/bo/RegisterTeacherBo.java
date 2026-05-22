package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * U 卡 段⑧ — 老师注册入参 BO。
 *
 * <p>路径 {@code POST /teacher/user/register}（无 @SaCheckLogin）。
 *
 * <p>第一版极简：只收用户名 + 密码 + 真名。无需验证码 / 手机 / 邮箱，
 * 后端会自动：① BCrypt 哈希密码 ② INSERT sys_user ③ 绑 teacher 角色。
 *
 * @author backend-dev
 */
@Data
public class RegisterTeacherBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名（登录账号），4-20 字符 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需 4-20 字符")
    private String userName;

    /** 密码明文（BE 端 BCrypt 哈希），6-20 字符 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需 6-20 字符")
    private String password;

    /** 真名 / 昵称（可选 — 不传时默认用 userName） */
    @Size(max = 30, message = "真名不超过 30 字符")
    private String nickName;
}
