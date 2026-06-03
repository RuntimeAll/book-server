package org.dromara.bookadmin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.bookadmin.domain.bo.AdminPaperEditBo;
import org.dromara.bookadmin.domain.bo.AdminPaperPageBo;
import org.dromara.bookadmin.service.IAdminPaperService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * admin 卷库 Service 实现（PRD-B-006 round-2 — G3 修复）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本实现物理落 {@code ruoyi-book-admin} 模块，
 * 依赖 {@code ruoyi-book} 共享 Mapper / Entity（DDL 单源），但方法内部
 * <strong>直接走 Mapper 自查</strong>，<strong>禁调</strong>
 * {@code org.dromara.book.service.IPaperLibraryService}（教师端 Service）的任何方法。
 *
 * <p>FE API 契约（admin/paper/index.ts）：
 * <ul>
 *   <li>POST /admin/paper/page → adminPage</li>
 *   <li>POST /admin/paper/select/{id} → adminSelectById</li>
 *   <li>POST /admin/paper/edit → adminEdit（id null=新建 / 有值=编辑）</li>
 *   <li>POST /admin/paper/delete/{id} → adminSoftDelete</li>
 * </ul>
 *
 * @author backend-dev (PRD-B-006 round-2)
 */
@Service
@RequiredArgsConstructor
public class AdminPaperServiceImpl implements IAdminPaperService {

    private final BizPaperMapper bizPaperMapper;

    /** misikt 默认每页 10，pageIndex 兜底 1（与教师端一致） */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_INDEX = 1;

    @Override
    public MisiktPageVo<BizPaper> adminPage(AdminPaperPageBo bo) {
        int pageIndex = bo.getPageIndex() == null || bo.getPageIndex() <= 0
            ? DEFAULT_PAGE_INDEX : bo.getPageIndex();
        int pageSize = bo.getPageSize() == null || bo.getPageSize() <= 0
            ? DEFAULT_PAGE_SIZE : bo.getPageSize();

        QueryWrapper<BizPaper> wrapper = buildAdminPageWrapper(bo);

        Page<BizPaper> mpPage = new Page<>(pageIndex, pageSize);
        IPage<BizPaper> result = bizPaperMapper.selectPage(mpPage, wrapper);
        return MisiktPageVo.of(result);
    }

    /**
     * 构造 admin paper page 查询 WHERE 条件。
     *
     * <p>与教师端 {@code PaperLibraryServiceImpl#page} 的差异：
     * <ul>
     *   <li>admin 放开 status 过滤：不传 / 空 → 看全部状态（含草稿 '0' / 软删 '2'）</li>
     *   <li>教师端固定 {@code WHERE status='1'}（只返已发布卷）</li>
     * </ul>
     */
    private QueryWrapper<BizPaper> buildAdminPageWrapper(AdminPaperPageBo bo) {
        QueryWrapper<BizPaper> w = new QueryWrapper<>();

        // status 筛选：admin 不传 / 空 → 不过滤（看全部状态）
        String statusFilter = bo.getStatus();
        if (statusFilter != null && !statusFilter.isEmpty()) {
            w.eq("status", statusFilter);
        }

        // 试卷名模糊匹配
        if (bo.getName() != null && !bo.getName().isEmpty()) {
            w.like("name", bo.getName());
        }

        // 分类 id 前缀匹配（防 SQL 注入：subjectId 是 misikt 数字编码）
        if (bo.getSubjectId() != null && !bo.getSubjectId().isEmpty()) {
            String sid = bo.getSubjectId();
            if (!sid.matches("^\\d+$")) {
                w.apply("1=0");
            } else {
                w.likeRight("subject_id", sid);
            }
        }

        w.orderByDesc("sort").orderByDesc("id");
        return w;
    }

    @Override
    public BizPaper adminSelectById(Long id) {
        if (id == null) {
            return null;
        }
        // admin 通道不限 status — 让 admin 能查草稿 / 软删（教师端限 status='1'）
        return bizPaperMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long adminEdit(AdminPaperEditBo bo) {
        if (bo == null) {
            throw new ServiceException("入参不能为空");
        }
        if (bo.getName() == null || bo.getName().isEmpty()) {
            throw new ServiceException("试卷名不能为空");
        }

        boolean isCreate = bo.getId() == null;
        Long currentUserId = LoginHelper.getUserId();
        String userIdStr = String.valueOf(currentUserId);
        Date now = new Date();

        if (isCreate) {
            // 新建：INSERT biz_paper (status='0' 草稿，paper_type=1 手工)
            BizPaper entity = new BizPaper();
            entity.setName(bo.getName());
            entity.setSubjectId(bo.getSubjectId());
            entity.setPaperCategoryId(bo.getPaperCategoryId());
            entity.setDirectoryName(bo.getDirectoryName());
            entity.setSuggestTime(bo.getSuggestTime());
            entity.setExamYear(bo.getExamYear());
            entity.setRemark(bo.getRemark());
            entity.setQuestionCount(0);
            entity.setPaperType(1);   // 手工
            entity.setIsShare(0);
            entity.setStatus("0");    // 草稿
            entity.setSort(0);
            entity.setCreateBy(userIdStr);
            entity.setCreateTime(now);
            entity.setUpdateBy(userIdStr);
            entity.setUpdateTime(now);
            bizPaperMapper.insert(entity);
            Long paperId = entity.getId();
            if (paperId == null) {
                throw new ServiceException("新建试卷失败：未拿到自增 id");
            }
            return paperId;
        } else {
            // 编辑：UPDATE biz_paper（status 不动）
            Long paperId = bo.getId();
            BizPaper exists = bizPaperMapper.selectById(paperId);
            if (exists == null) {
                throw new ServiceException("试卷不存在: " + paperId);
            }
            if ("2".equals(exists.getStatus())) {
                throw new ServiceException("试卷已软删，不能编辑");
            }
            UpdateWrapper<BizPaper> w = new UpdateWrapper<>();
            w.eq("id", paperId)
                .set("name", bo.getName())
                .set("subject_id", bo.getSubjectId())
                .set("paper_category_id", bo.getPaperCategoryId())
                .set("directory_name", bo.getDirectoryName())
                .set("suggest_time", bo.getSuggestTime())
                .set("exam_year", bo.getExamYear())
                .set("remark", bo.getRemark())
                .set("update_by", userIdStr)
                .set("update_time", now);
            int affected = bizPaperMapper.update(null, w);
            if (affected == 0) {
                throw new ServiceException("试卷更新失败（影响行数 0）: " + paperId);
            }
            return paperId;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminSoftDelete(Long id) {
        if (id == null) {
            throw new ServiceException("试卷 ID 不能为空");
        }
        BizPaper exists = bizPaperMapper.selectById(id);
        if (exists == null) {
            throw new ServiceException("试卷不存在: " + id);
        }
        if ("2".equals(exists.getStatus())) {
            throw new ServiceException("试卷已软删，不能重复删除");
        }
        // 软删：UPDATE biz_paper SET status='2', update_time=NOW() WHERE id=?
        UpdateWrapper<BizPaper> w = new UpdateWrapper<>();
        w.eq("id", id)
            .set("status", "2")
            .set("update_time", new Date());
        bizPaperMapper.update(null, w);
    }
}
