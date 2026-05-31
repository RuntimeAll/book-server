package org.dromara.book.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * PRD-002 — 教师个人资料更新入参 BO。
 *
 * <p>路径 {@code POST /teacher/user/update}（@SaCheckLogin）。
 *
 * <p>userId 不在 body —— 从登录态 {@code LoginHelper.getUserId()} 取，防越权改他人。
 * 写 sys_user 四字段：nick_name(←realName) / sex / grade / school。
 *
 * @author backend-dev
 */
@Data
public class UpdateProfileBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 真实姓名（→ nick_name），必填 */
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 30, message = "真实姓名不超过 30 字符")
    private String realName;

    /** 性别：0=男 / 1=女 / null=不改（可空） */
    private Integer sex;

    /** 任教年级，必填 */
    @NotBlank(message = "任教年级不能为空")
    @Size(max = 50, message = "任教年级不超过 50 字符")
    private String grade;

    /** 任教学校（可空） */
    @Size(max = 100, message = "学校不超过 100 字符")
    private String school;
}
