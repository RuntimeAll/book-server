package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.CurrentUserVo;
import org.dromara.book.service.IUserService;
import org.dromara.system.domain.SysUser;
import org.dromara.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

/**
 * 当前用户 Service 实现。
 *
 * <p>复用 ruoyi-system 的 {@link SysUserMapper}（BaseMapper.selectById），不重造。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final SysUserMapper sysUserMapper;

    @Override
    public CurrentUserVo getCurrent(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null) {
            return null;
        }
        CurrentUserVo vo = new CurrentUserVo();
        vo.setId(u.getUserId());
        vo.setUserName(u.getUserName());
        vo.setRealName(u.getNickName());
        vo.setPhone(u.getPhonenumber());
        // 教师固定 role = 2（misikt 风格：2=教师，本工程不复刻学生）
        vo.setRole(2);
        // avatar 在 RuoYi 是 sys_oss 引用 Long；V0.1 直接 toString 让 FE 拿到非 null（不退化）
        vo.setImagePath(u.getAvatar() == null ? null : String.valueOf(u.getAvatar()));
        // 不复刻字段全 null（保持 FE TS interface 字段齐全）
        vo.setUserUuid(null);
        vo.setMember(null);
        vo.setExpireTime(null);
        vo.setDailyEeQuota(null);
        vo.setQuestionBasketMax(null);
        vo.setPaperBasketMax(null);
        return vo;
    }
}
