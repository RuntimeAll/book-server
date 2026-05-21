package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * /teacher/user/current 响应 VO。
 *
 * <p>misikt 风格字段命名（驼峰）。本工程不复刻 misikt 的会员 / 配额 / UUID — 这些字段返 null
 * 保持 FE TS interface（CurrentUserVO）字段齐全（FE 拿 null 仍能跑）。
 *
 * @author backend-dev
 */
@Data
public class CurrentUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * sys_user.user_id
     */
    private Long id;

    /**
     * misikt 字段（基于微信 OpenID 风格）— RuoYi 无此字段，返 null
     */
    private String userUuid;

    /**
     * sys_user.user_name
     */
    private String userName;

    /**
     * 真名：sys_user.nick_name
     */
    private String realName;

    /**
     * 手机号：sys_user.phonenumber
     */
    private String phone;

    /**
     * 教师固定 = 2（misikt 风格：2=教师 / 1=学生，本工程不复刻学生 role）
     */
    private Integer role;

    /**
     * 头像：sys_user.avatar（RuoYi 实际是 sys_oss 引用 Long，V0.1 直接 toString，FE 不渲染头像也不退化）
     */
    private String imagePath;

    /**
     * 会员标识（不复刻，固定 null）
     */
    private Boolean member;

    /**
     * 会员过期时间（不复刻，固定 null）
     */
    private String expireTime;

    /**
     * 每日 AI 配额（不复刻，固定 null）
     */
    private Integer dailyEeQuota;

    /**
     * 试题筐上限（不复刻 — V0.1 无上限，固定 null）
     */
    private Integer questionBasketMax;

    /**
     * 试卷筐上限（不复刻 — V0.1 无上限，固定 null）
     */
    private Integer paperBasketMax;
}
