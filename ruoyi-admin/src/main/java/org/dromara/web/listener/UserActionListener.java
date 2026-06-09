package org.dromara.web.listener;

import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.convert.Convert;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.CacheConstants;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.domain.dto.UserOnlineDTO;
import org.dromara.common.core.utils.MessageUtils;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.ip.AddressUtils;
import org.dromara.common.log.event.LogininforEvent;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.web.service.SysLoginService;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 用户行为 侦听器的实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class UserActionListener implements SaTokenListener {

    private final SysLoginService loginService;

    /**
     * 每次登录时触发
     */
    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        UserAgent userAgent = UserAgentUtil.parse(ServletUtils.getRequest().getHeader("User-Agent"));
        String ip = ServletUtils.getClientIP();
        UserOnlineDTO dto = new UserOnlineDTO();
        dto.setIpaddr(ip);
        dto.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        dto.setBrowser(userAgent.getBrowser().getName());
        dto.setOs(userAgent.getOs().getName());
        dto.setLoginTime(System.currentTimeMillis());
        dto.setTokenId(tokenValue);
        String username = (String) loginParameter.getExtra(LoginHelper.USER_NAME_KEY);
        String tenantId = (String) loginParameter.getExtra(LoginHelper.TENANT_KEY);
        dto.setUserName(username);
        dto.setClientKey((String) loginParameter.getExtra(LoginHelper.CLIENT_KEY));
        dto.setDeviceType(loginParameter.getDeviceType());
        dto.setDeptName((String) loginParameter.getExtra(LoginHelper.DEPT_NAME_KEY));
        TenantHelper.dynamic(tenantId, () -> {
            if(loginParameter.getTimeout() == -1) {
                RedisUtils.setCacheObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue, dto);
            } else {
                RedisUtils.setCacheObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue, dto, Duration.ofSeconds(loginParameter.getTimeout()));
            }
        });
        // 记录登录日志
        LogininforEvent logininforEvent = new LogininforEvent();
        logininforEvent.setTenantId(tenantId);
        logininforEvent.setUsername(username);
        logininforEvent.setStatus(Constants.LOGIN_SUCCESS);
        logininforEvent.setMessage(MessageUtils.message("user.login.success"));
        logininforEvent.setRequest(ServletUtils.getRequest());
        SpringUtils.context().publishEvent(logininforEvent);
        // 更新登录信息
        loginService.recordLoginInfo((Long) loginParameter.getExtra(LoginHelper.USER_KEY), ip);
        log.info("user doLogin, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 每次注销时触发
     */
    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        cleanOnlineToken(tokenValue);
        log.info("user doLogout, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 每次被踢下线时触发
     */
    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        cleanOnlineToken(tokenValue);
        log.info("user doKickout, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 每次被顶下线时触发
     */
    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        cleanOnlineToken(tokenValue);
        log.info("user doReplaced, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 删除指定 token 的在线用户缓存（注销 / 踢下线 / 顶下线共用）。
     *
     * <p>🔴 PRD-A-012 D-1 收尾（line-a-dev 2026-06-09）：jwt 密钥轮换 / BE 换密钥重启后，
     * Redis 里残留的旧会话 token 是用<b>旧密钥</b>签发的。本监听器原先直接
     * {@code StpUtil.getExtra(tokenValue, ...)} 解析该 token 取租户，当前密钥验签旧 token 失败 →
     * 抛 {@code SaJwtException} 冒泡到 {@code createLoginSession → logoutByMaxLoginCount} →
     * 整个 /auth/login 500。这会在 <b>prod 首次设置 / 轮换 SA_JWT_SECRET</b> 时复现（landmine）。
     *
     * <p>修复：解析旧 token 失败时容错——该 token 本就在被注销，extra 取不到无碍；
     * 用 try 解析到的租户精确删 online key，解析失败则裸删（删不掉也随 token TTL 自然过期），
     * 绝不让一个解不开的旧 token 阻断正常登录。
     */
    private void cleanOnlineToken(String tokenValue) {
        String tenantId = null;
        try {
            tenantId = Convert.toStr(StpUtil.getExtra(tokenValue, LoginHelper.TENANT_KEY));
        } catch (Exception e) {
            // 旧 token 用旧 jwt 密钥签发，当前密钥验签失败（密钥轮换 / BE 换密钥重启场景）
            log.warn("解析旧 token 取租户失败（疑似 jwt 密钥已轮换），降级裸删 online 缓存: ***{} ({})",
                StringUtils.right(tokenValue, 8), e.getMessage());
        }
        if (tenantId != null) {
            TenantHelper.dynamic(tenantId, () -> {
                RedisUtils.deleteObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue);
            });
        } else {
            RedisUtils.deleteObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue);
        }
    }

    /**
     * 每次被封禁时触发
     */
    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
    }

    /**
     * 每次被解封时触发
     */
    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
    }

    /**
     * 每次打开二级认证时触发
     */
    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
    }

    /**
     * 每次创建Session时触发
     */
    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {
    }

    /**
     * 每次创建Session时触发
     */
    @Override
    public void doCreateSession(String id) {
    }

    /**
     * 每次注销Session时触发
     */
    @Override
    public void doLogoutSession(String id) {
    }

    /**
     * 每次Token续期时触发
     */
    @Override
    public void doRenewTimeout(String loginType, Object loginId, String tokenValue, long timeout) {
    }
}
