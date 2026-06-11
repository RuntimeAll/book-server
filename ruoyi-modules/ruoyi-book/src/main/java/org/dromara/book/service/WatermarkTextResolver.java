package org.dromara.book.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.system.domain.SysUser;
import org.dromara.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 水印文字解析（PRD-A-014）：从 user_id 查 sys_user，拼 "老师 realName + 脱敏手机号"。
 *
 * <p>realName = sys_user.nick_name（项目约定，同 UserServiceImpl），手机号脱敏
 * {@code 138****1234}（前 3 后 4 中间 ****）。水印文字在 Worker 里算（异步线程无登录上下文，
 * 故按 record.user_id 显式查，不走 LoginHelper）。
 *
 * @author backend-dev
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatermarkTextResolver {

    private final SysUserMapper sysUserMapper;

    /**
     * 拼水印文字。
     *
     * @param userId 导出发起者 sys_user.id
     * @return "realName 138****1234"，查不到用户兜底空串（不让水印崩坏整卷）
     */
    public String resolve(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            SysUser u = sysUserMapper.selectById(userId);
            if (u == null) {
                return "";
            }
            String realName = StringUtils.defaultString(u.getNickName());
            String phone = maskPhone(u.getPhonenumber());
            return (realName + "  " + phone).trim();
        } catch (Exception e) {
            log.warn("[watermark] 解析用户 {} 失败: {}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * 手机号脱敏：13812341234 → 138****1234（前 3 后 4，中间 ****）。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return StringUtils.defaultString(phone);
        }
        String p = phone.trim();
        if (p.length() < 7) {
            return p;
        }
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }
}
