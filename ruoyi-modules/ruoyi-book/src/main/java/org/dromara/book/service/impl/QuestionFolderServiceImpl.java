package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionFolderBo;
import org.dromara.book.domain.entity.BizQuestionFavorite;
import org.dromara.book.domain.entity.BizQuestionFolder;
import org.dromara.book.domain.vo.QuestionFolderVo;
import org.dromara.book.mapper.BizQuestionFavoriteMapper;
import org.dromara.book.mapper.BizQuestionFolderMapper;
import org.dromara.book.service.IQuestionFolderService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 题目收藏夹 Service 实现（PRD-A-005 收尾 B-收藏夹 CRUD）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>范围一律限当前登录 user_id（LoginHelper.getUserId()），绝不信任前端传的归属字段</li>
 *   <li>biz_question_folder / biz_question_favorite 均无 tenant_id 列：Wrapper 式方法靠 Mapper
 *       类级 @InterceptorIgnore 挡多租户；selectById / updateById 等 BaseMapper 继承方法的
 *       namespace 命中不到类级注解（PRD-A-005 G4 教训），故涉及继承方法处再叠
 *       TenantHelper.ignore(DataPermissionHelper.ignore(...)) 线程级双保险</li>
 *   <li>folderTree 始终首位带虚拟默认夹 {id:0,name:"我的试题"}（folder_id=0 的散收藏），兼容前端兜底契约</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class QuestionFolderServiceImpl implements IQuestionFolderService {

    private final BizQuestionFolderMapper bizQuestionFolderMapper;
    private final BizQuestionFavoriteMapper bizQuestionFavoriteMapper;

    /** 默认夹（散收藏 folder_id=0）展示名 */
    private static final String DEFAULT_FOLDER_NAME = "我的试题";
    private static final long DEFAULT_FOLDER_ID = 0L;

    private Long requireUserId() {
        Long uid = LoginHelper.getUserId();
        if (uid == null) {
            throw new ServiceException("未登录用户不能操作收藏夹");
        }
        return uid;
    }

    /** 统计某用户某夹下收藏题数（folder_id 维度） */
    private int countFavorites(Long userId, Long folderId) {
        LambdaQueryWrapper<BizQuestionFavorite> w = new LambdaQueryWrapper<>();
        w.eq(BizQuestionFavorite::getUserId, userId);
        w.eq(BizQuestionFavorite::getFolderId, folderId);
        return Math.toIntExact(bizQuestionFavoriteMapper.selectCount(w));
    }

    @Override
    public List<QuestionFolderVo> folderTree() {
        Long userId = requireUserId();

        List<QuestionFolderVo> result = new ArrayList<>();

        // 1. 虚拟默认夹「我的试题」(id=0)：folder_id=0 的散收藏数
        QuestionFolderVo def = new QuestionFolderVo();
        def.setId(DEFAULT_FOLDER_ID);
        def.setName(DEFAULT_FOLDER_NAME);
        def.setPid(0L);
        def.setCount(countFavorites(userId, DEFAULT_FOLDER_ID));
        result.add(def);

        // 2. 用户自建夹（按 sort / create_time 排序）
        LambdaQueryWrapper<BizQuestionFolder> w = new LambdaQueryWrapper<>();
        w.eq(BizQuestionFolder::getUserId, userId);
        w.orderByAsc(BizQuestionFolder::getSort).orderByAsc(BizQuestionFolder::getCreateTime);
        List<BizQuestionFolder> folders = bizQuestionFolderMapper.selectList(w);
        if (folders != null) {
            for (BizQuestionFolder f : folders) {
                QuestionFolderVo vo = new QuestionFolderVo();
                vo.setId(f.getId());
                vo.setName(f.getName());
                vo.setPid(f.getPid());
                vo.setSort(f.getSort());
                vo.setCreateTime(f.getCreateTime());
                vo.setCount(countFavorites(userId, f.getId()));
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFolder(QuestionFolderBo bo) {
        Long userId = requireUserId();
        if (bo == null || bo.getName() == null || bo.getName().trim().isEmpty()) {
            throw new ServiceException("收藏夹名称不能为空");
        }
        BizQuestionFolder folder = new BizQuestionFolder();
        folder.setUserId(userId);
        folder.setName(bo.getName().trim());
        folder.setPid(bo.getPid() == null ? 0L : bo.getPid());
        folder.setSort(0);
        folder.setCreateTime(new Date());
        bizQuestionFolderMapper.insert(folder);
        return folder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameFolder(QuestionFolderBo bo) {
        Long userId = requireUserId();
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("收藏夹ID不能为空");
        }
        if (bo.getName() == null || bo.getName().trim().isEmpty()) {
            throw new ServiceException("收藏夹名称不能为空");
        }
        if (DEFAULT_FOLDER_ID == bo.getId()) {
            throw new ServiceException("默认收藏夹不可改名");
        }
        // selectById 是 BaseMapper 继承方法 → TenantHelper.ignore 包裹规避无 tenant_id 列报错
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            BizQuestionFolder existing = bizQuestionFolderMapper.selectById(bo.getId());
            if (existing == null) {
                throw new ServiceException("收藏夹不存在: " + bo.getId());
            }
            // 越权防护：只能改本人的夹
            if (!userId.equals(existing.getUserId())) {
                throw new ServiceException("无权修改非本人的收藏夹");
            }
            // 仅更新 name（用 LambdaUpdateWrapper 走 Wrapper 路径，类级 @InterceptorIgnore 生效）
            LambdaUpdateWrapper<BizQuestionFolder> u = new LambdaUpdateWrapper<>();
            u.eq(BizQuestionFolder::getId, bo.getId());
            u.eq(BizQuestionFolder::getUserId, userId);
            u.set(BizQuestionFolder::getName, bo.getName().trim());
            bizQuestionFolderMapper.update(null, u);
            return null;
        }));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(QuestionFolderBo bo) {
        Long userId = requireUserId();
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("收藏夹ID不能为空");
        }
        if (DEFAULT_FOLDER_ID == bo.getId()) {
            throw new ServiceException("默认收藏夹不可删除");
        }
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            BizQuestionFolder existing = bizQuestionFolderMapper.selectById(bo.getId());
            if (existing == null) {
                throw new ServiceException("收藏夹不存在: " + bo.getId());
            }
            if (!userId.equals(existing.getUserId())) {
                throw new ServiceException("无权删除非本人的收藏夹");
            }
            // 1. 该夹下收藏题 folder_id 重置为 0（归默认夹，不丢用户收藏的题）
            LambdaUpdateWrapper<BizQuestionFavorite> favUpd = new LambdaUpdateWrapper<>();
            favUpd.eq(BizQuestionFavorite::getUserId, userId);
            favUpd.eq(BizQuestionFavorite::getFolderId, bo.getId());
            favUpd.set(BizQuestionFavorite::getFolderId, DEFAULT_FOLDER_ID);
            bizQuestionFavoriteMapper.update(null, favUpd);

            // 2. 删 folder（用 Wrapper 路径 + user_id 双条件，类级 @InterceptorIgnore 生效）
            LambdaQueryWrapper<BizQuestionFolder> del = new LambdaQueryWrapper<>();
            del.eq(BizQuestionFolder::getId, bo.getId());
            del.eq(BizQuestionFolder::getUserId, userId);
            bizQuestionFolderMapper.delete(del);
            return null;
        }));
    }
}
