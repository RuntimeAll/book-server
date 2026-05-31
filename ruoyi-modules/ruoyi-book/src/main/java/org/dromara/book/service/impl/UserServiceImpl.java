package org.dromara.book.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.book.domain.bo.RegisterTeacherBo;
import org.dromara.book.domain.bo.UpdateProfileBo;
import org.dromara.book.domain.vo.CurrentUserVo;
import org.dromara.book.service.IUserService;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

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
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    /** U 卡 — 教师角色 role_key（FE 登录分流 + BE 注册时绑定都用这个 key） */
    private static final String TEACHER_ROLE_KEY = "teacher";

    /** RuoYi 默认租户 ID（单租户模式） */
    private static final String DEFAULT_TENANT_ID = "000000";

    /** RuoYi 默认部门 ID（admin 同部门 — 教师挂这里） */
    private static final Long DEFAULT_DEPT_ID = 103L;

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
        // PRD-002 — sex/grade/school 回填。sex 是 char(1) "0"/"1"/"2"，转 Integer（非 0/1 → null）
        vo.setSex(parseSex(u.getSex()));
        vo.setGrade(u.getGrade());
        vo.setSchool(u.getSchool());
        // 教师固定 role = 2（misikt 风格：2=教师，本工程不复刻学生）
        vo.setRole(2);
        // U 卡新增 — 真实角色 role_key 集合（FE 登录分流用）
        LoginUser loginUser = LoginHelper.getLoginUser();
        vo.setRoles(loginUser != null && loginUser.getRolePermission() != null
            ? loginUser.getRolePermission() : Collections.emptySet());
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

    /**
     * U 卡 段⑧ — 老师注册：建 sys_user + 绑 teacher 角色（一事务）。
     *
     * <p>事务保证：用户已建但绑角色失败时回滚，避免 sys_user 留孤行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterTeacherBo bo) {
        // 1. 重复检查
        boolean exists = sysUserMapper.exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUserName, bo.getUserName()));
        if (exists) {
            throw new ServiceException("用户名 " + bo.getUserName() + " 已被注册");
        }

        // 2. 反查 teacher 角色 ID（避免硬编码 — dev / prod 可能 ID 不同）
        SysRole teacherRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleKey, TEACHER_ROLE_KEY)
            .eq(SysRole::getStatus, "0"));
        if (teacherRole == null) {
            throw new ServiceException("教师角色未配置（sys_role.role_key='teacher'），请联系管理员");
        }

        // 3. 建 SysUser — MP ASSIGN_ID 自动注入雪花 userId
        SysUser user = new SysUser();
        user.setUserName(bo.getUserName());
        user.setNickName(bo.getNickName() == null || bo.getNickName().isEmpty()
            ? bo.getUserName() : bo.getNickName());
        user.setPassword(BCrypt.hashpw(bo.getPassword()));
        user.setUserType("sys_user");
        user.setTenantId(DEFAULT_TENANT_ID);
        user.setDeptId(DEFAULT_DEPT_ID);
        user.setStatus("0");
        user.setDelFlag("0");
        user.setCreateBy(0L);
        user.setUpdateBy(0L);
        sysUserMapper.insert(user);

        // 4. 绑 teacher 角色（insert 后 user.getUserId() 已被 MP 回填）
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(teacherRole.getRoleId());
        sysUserRoleMapper.insert(userRole);

        return user.getUserId();
    }

    /**
     * PRD-002 — 更新当前登录教师个人资料。
     *
     * <p>userId 由 Controller 从 LoginHelper 传入（不从 body 收，防越权）。
     * 写 sys_user：nick_name(←realName) / sex / grade / school。
     */
    @Override
    public void updateProfile(Long userId, UpdateProfileBo bo) {
        if (userId == null) {
            throw new ServiceException("未获取到当前登录用户");
        }
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null) {
            throw new ServiceException("用户不存在");
        }
        // 显式 LambdaUpdateWrapper.set(...) 四列：支持把 sex/school 写为 null（清空语义，表单即真相）。
        LambdaUpdateWrapper<SysUser> luw = Wrappers.<SysUser>lambdaUpdate()
            .eq(SysUser::getUserId, userId)
            .set(SysUser::getNickName, bo.getRealName())
            // sex：Integer 0/1 → char(1) "0"/"1"；null → 写 null（表单未选=无性别）
            .set(SysUser::getSex, bo.getSex() == null ? null : String.valueOf(bo.getSex()))
            .set(SysUser::getGrade, bo.getGrade())
            // school 可空：null/"" 都按表单原样写入（用户清空学校）
            .set(SysUser::getSchool, bo.getSchool());
        // 🔴 PRD-002 G3 根因：RuoYi 数据权限拦截器给 UPDATE sys_user 注入 AND create_by=<登录用户id>，
        // teacher001 的 create_by≠5 → 0 行更新（静默假成功）。自己改自己资料（userId 取自登录态，无越权风险）
        // 必须绕过数据权限 —— 与框架自带 SysProfileController.updateProfile / SysLoginService 登录写 login_date 同款。
        DataPermissionHelper.ignore(() -> sysUserMapper.update(null, luw));
    }

    /**
     * sys_user.sex（char(1) "0"/"1"/"2"）→ CurrentUserVo.sex（Integer 0男/1女/null）。
     * 仅 "0"/"1" 映射为 0/1，其余（"2" 未知 / null / 空）→ null。
     */
    private Integer parseSex(String sex) {
        if ("0".equals(sex)) {
            return 0;
        }
        if ("1".equals(sex)) {
            return 1;
        }
        return null;
    }
}
