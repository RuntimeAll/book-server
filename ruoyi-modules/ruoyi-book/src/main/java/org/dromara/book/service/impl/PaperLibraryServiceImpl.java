package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.bo.CreateExamPaperBo;
import org.dromara.book.domain.bo.PaperLazyTreeBo;
import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.bo.UpdateExamPaperBo;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.entity.BizPaperCategory;
import org.dromara.book.domain.entity.BizPaperQuestion;
import org.dromara.book.domain.entity.BizPaperSection;
import org.dromara.book.domain.vo.CreateExamPaperVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.PaperCategoryNodeVo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.mapper.BizPaperSectionMapper;
import org.dromara.book.service.IPaperLibraryService;
import org.dromara.book.service.paper.PaperCompositionService;
import org.dromara.book.service.paper.SelectionRules;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 卷库 Service 实现（D 卡 V0.5 卷库视觉级还原）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>lazyTree：拉全表 → 内存建树 → 3 根（按 misikt 真响应排序 3003 / 3001 / 3004 / 等 sort）</li>
 *   <li>page：MyBatis-Plus Page + QueryWrapper（name LIKE / subject_id 前缀 / status='1'）</li>
 *   <li>3001 根节点 parentId override 为 "1"（misikt 真响应历史遗留 bug，字节级对齐）</li>
 * </ul>
 *
 * <p>公开查询只读取官方普通卷；分类用于检索，不授予读取权限。
 *
 * @author backend-dev
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperLibraryServiceImpl implements IPaperLibraryService {

    private final BizPaperCategoryMapper bizPaperCategoryMapper;
    private final BizPaperMapper bizPaperMapper;
    private final BizPaperSectionMapper bizPaperSectionMapper;
    private final BizPaperQuestionMapper bizPaperQuestionMapper;
    private final PaperCompositionService compositionService;

    /** misikt 默认每页 10，pageIndex 兜底 1 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_INDEX = 1;

    /**
     * misikt 真响应里 3001 公共试卷根节点的 parentId 是 "1" 不是 "0"（历史 bug + 字节级对齐）。
     * 其他根节点（3003 资料库 / 3004 专题卷库）parentId 都是 "0"。
     */
    private static final String ROOT_3001_PARENT_ID_OVERRIDE = "1";

    /** 三个根 id（用于在内存建树时识别根节点） */
    private static final Set<String> ROOT_IDS = Set.of("3001", "3003", "3004");

    @Override
    public List<PaperCategoryNodeVo> lazyTree(PaperLazyTreeBo bo) {
        // V0.5 忽略 bo.type / bo.version — 一次性返整树
        QueryWrapper<BizPaperCategory> wrapper = new QueryWrapper<>();
        wrapper.notLike("parent_id", "-");  // 过滤 deprecated 软删行（parent_id='-deprecated'）
        List<BizPaperCategory> all = bizPaperCategoryMapper.selectList(wrapper);
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 实体 → VO，按 id 建索引
        Map<String, PaperCategoryNodeVo> idMap = new HashMap<>(all.size() * 2);
        for (BizPaperCategory e : all) {
            idMap.put(e.getId(), toVo(e));
        }

        // 2. 串父子关系
        List<PaperCategoryNodeVo> roots = new ArrayList<>();
        for (BizPaperCategory e : all) {
            PaperCategoryNodeVo node = idMap.get(e.getId());
            if (ROOT_IDS.contains(e.getId())) {
                roots.add(node);
                continue;
            }
            PaperCategoryNodeVo parent = e.getParentId() == null ? null : idMap.get(e.getParentId());
            if (parent == null) {
                // 父不在结果集 — 当作根
                roots.add(node);
            } else {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }

        // 3. 按 sort asc 排序 + 标 hasChildren
        sortRecursive(roots);
        markHasChildren(roots);

        return roots;
    }

    /**
     * 单实体 → VO。字段口径按 misikt 真响应字节级：
     * 3001 根节点 parentId override '1'。
     * 🔴 PRD-B-013 减法：共享标记列已 DROP，VO 同步删除该字段。
     */
    private PaperCategoryNodeVo toVo(BizPaperCategory e) {
        PaperCategoryNodeVo vo = new PaperCategoryNodeVo();
        vo.setId(e.getId());
        // 3001 根的 parentId override 为 "1" — misikt 真响应历史遗留
        if ("3001".equals(e.getId())) {
            vo.setParentId(ROOT_3001_PARENT_ID_OVERRIDE);
        } else {
            vo.setParentId(e.getParentId());
        }
        vo.setTitle(e.getName());
        vo.setKey(e.getId());
        vo.setValue(e.getId());
        vo.setLevel(null);                 // misikt 真响应恒 null
        vo.setSort(e.getSort());
        vo.setNodeDataSum(null);           // misikt 真响应恒 null
        // 结构化维度（字典码，语义下沉每节点；前端按码筛选不再解析 title）
        vo.setSubject(e.getSubject());
        vo.setStage(e.getStage());
        vo.setGrade(e.getGrade());
        vo.setVolume(e.getVolume());
        vo.setPaperType(e.getPaperType());
        vo.setNodeKind(e.getNodeKind());
        // children / hasChildren 留 markHasChildren 阶段处理
        return vo;
    }

    /**
     * 递归按 sort asc 排序子节点。
     */
    private void sortRecursive(List<PaperCategoryNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        nodes.sort(Comparator.comparing(PaperCategoryNodeVo::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
        for (PaperCategoryNodeVo n : nodes) {
            sortRecursive(n.getChildren());
        }
    }

    /**
     * 标 hasChildren：叶节点 false / 非叶子 true（misikt 真响应两种都显式带）。
     */
    private void markHasChildren(List<PaperCategoryNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        for (PaperCategoryNodeVo n : nodes) {
            if (n.getChildren() == null || n.getChildren().isEmpty()) {
                n.setHasChildren(false);
                n.setChildren(null);            // 叶节点 children 字段不出现（@JsonInclude NON_NULL 兜底）
            } else {
                n.setHasChildren(true);
                markHasChildren(n.getChildren());
            }
        }
    }

    @Override
    public MisiktPageVo<PaperListItemVo> page(PaperPageBo bo) {
        int pageIndex = bo.getPageIndex() == null || bo.getPageIndex() <= 0
            ? DEFAULT_PAGE_INDEX : bo.getPageIndex();
        int pageSize = bo.getPageSize() == null || bo.getPageSize() <= 0
            ? DEFAULT_PAGE_SIZE : Math.min(bo.getPageSize(), SelectionRules.MAX_BATCH_SIZE);

        // 数据隔离：取当前登录用户 userId（create_by 存数字字符串）
        // PRD-C-212 D5（2026-07-04）：公共卷库(scope!=mine)放开游客只读浏览（security.excludes
        // 已放行 page/lazyTree/detail 只读端点）；「我的卷库」仍必须登录——登录闸只收窄到 mine 分支。
        Long currentUserId = LoginHelper.getUserId();

        QueryWrapper<PaperListItemVo> wrapper = new QueryWrapper<>();

        // mine 按本人归属；public 仅官方卷，分类筛选不能将私人卷变成公共卷。
        if ("mine".equals(bo.getScope())) {
            if (currentUserId == null) {
                throw new ServiceException("未登录用户不能访问我的卷库", 401);
            }
            // 我的卷库：只看当前登录用户自己创建的，绝不信任前端传的 createBy
            wrapper.eq("p.create_by", String.valueOf(currentUserId));
            // PRD-B-101：mine 口径支持按卷型筛（'2'=备课卷 tab / '1'=普通卷）；空则不过滤（全量含备课卷）
            if (bo.getPaperKind() != null && !bo.getPaperKind().isBlank()) {
                wrapper.eq("p.paper_kind", bo.getPaperKind());
            }
            wrapper.in("p.status", List.of(BizPaper.STATUS_DRAFT, BizPaper.STATUS_PUBLISHED));
        } else {
            // 分类只负责检索，不能作为公开授权依据。
            wrapper.eq("p.create_by", SelectionRules.OFFICIAL_OWNER_ID);
            wrapper.ne("p.paper_kind", "2");
            if (LoginHelper.isSuperAdmin(currentUserId)) {
                // 超管需要看到自己的未公开官方卷，才能重新公开。
                wrapper.in("p.status", List.of(BizPaper.STATUS_DRAFT, BizPaper.STATUS_PUBLISHED));
            } else {
                wrapper.eq("p.status", BizPaper.STATUS_PUBLISHED);
            }
        }

        if (bo.getName() != null && !bo.getName().isEmpty()) {
            wrapper.like("p.name", bo.getName());
        }
        if (bo.getSubjectId() != null && !bo.getSubjectId().isEmpty()) {
            // 防 SQL 注入：subjectId 是 misikt 自营 4-15 位数字编码
            String sid = bo.getSubjectId();
            if (!sid.matches("^\\d+$")) {
                // 非法 subjectId 直接返空集（不报错，misikt 真站也吞）
                wrapper.apply("1=0");
            } else {
                wrapper.likeRight("p.subject_id", sid);
            }
        }
        wrapper.orderByDesc("p.create_time", "p.id");

        Page<PaperListItemVo> mpPage = new Page<>(pageIndex, pageSize);
        IPage<PaperListItemVo> result = bizPaperMapper.selectPaperListPage(mpPage, wrapper);
        result.getRecords().forEach(item -> {
            String ownerId = item.getCreateUser() == null ? null : item.getCreateUser().toString();
            item.setPublished(BizPaper.STATUS_PUBLISHED.equals(String.valueOf(item.getStatus())));
            item.setCanManage(SelectionRules.canManagePaper(ownerId, currentUserId));
            item.setCanChangeVisibility(SelectionRules.canChangeVisibility(ownerId, item.getPaperKind(), currentUserId));
        });
        return MisiktPageVo.of(result);
    }

    @Override
    public CreateExamPaperVo createExamPaper(CreateExamPaperBo bo) {
        return compositionService.create(bo, LoginHelper.getUserId());
    }

    @Override
    public CreateExamPaperVo createExamPaperForTeacher(CreateExamPaperBo bo, Long teacherId) {
        return compositionService.create(bo, teacherId);
    }

    @Override
    public PaperDetailVo updateExamPaper(UpdateExamPaperBo bo) {
        return compositionService.update(bo, LoginHelper.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeVisibility(Long paperId, boolean published) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录不能切换试卷公开状态", 401);
        }
        if (paperId == null || paperId <= 0) {
            throw new ServiceException("试卷ID必须为正整数", 400);
        }
        if (!LoginHelper.isSuperAdmin(currentUserId)) {
            throw new ServiceException("仅超级管理员可切换试卷公开状态", 403);
        }

        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            BizPaper paper = bizPaperMapper.lockById(paperId);
            if (paper == null || (!BizPaper.STATUS_DRAFT.equals(paper.getStatus())
                && !BizPaper.STATUS_PUBLISHED.equals(paper.getStatus()))) {
                throw new ServiceException("试卷不存在", 404);
            }
            if (!SelectionRules.isOfficialOrdinaryPaper(paper.getCreateBy(), paper.getPaperKind())) {
                throw new ServiceException("无权切换非官方普通卷的公开状态", 403);
            }
            String targetStatus = published ? BizPaper.STATUS_PUBLISHED : BizPaper.STATUS_DRAFT;
            if (targetStatus.equals(paper.getStatus())) {
                return null;
            }
            LambdaUpdateWrapper<BizPaper> update = new LambdaUpdateWrapper<BizPaper>()
                .eq(BizPaper::getId, paperId)
                .eq(BizPaper::getCreateBy, SelectionRules.OFFICIAL_OWNER_ID)
                .ne(BizPaper::getPaperKind, "2")
                .in(BizPaper::getStatus, BizPaper.STATUS_DRAFT, BizPaper.STATUS_PUBLISHED)
                .set(BizPaper::getStatus, targetStatus)
                .set(BizPaper::getUpdateBy, currentUserId.toString())
                .set(BizPaper::getUpdateTime, new java.util.Date());
            if (bizPaperMapper.update(null, update) != 1) {
                throw new ServiceException("试卷公开状态更新失败");
            }
            log.info("【paper·visibility】 userId={}, paperId={}, published={}", currentUserId, paperId, published);
            return null;
        }));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExamPaper(Long paperId) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录用户不能删除试卷", 401);
        }
        if (paperId == null || paperId <= 0) {
            throw new ServiceException("试卷ID必须为正整数", 400);
        }

        // biz_paper / biz_paper_question / biz_paper_section 三表均无 tenant_id 列，
        // BaseMapper 继承方法（selectById / deleteById / delete 带 WHERE）会被多租户拦截器
        // 注入 AND tenant_id=? 报 Unknown column 'tenant_id'（PRD-A-005 G4 已踩）。
        // 故全事务体走 TenantHelper.ignore(DataPermissionHelper.ignore(...)) 线程级包裹。
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // Official papers are managed by the super administrator; private papers by their owner.
            BizPaper existing = bizPaperMapper.lockById(paperId);
            if (existing == null) {
                throw new ServiceException("试卷不存在: " + paperId, 404);
            }
            if (!SelectionRules.canManagePaper(existing.getCreateBy(), currentUserId)) {
                throw new ServiceException("无权删除非本人创建的试卷", 403);
            }

            log.info("【paper·delete】 userId={}, paperId={}, paperName={}", currentUserId, paperId, existing.getName());

            // 2. 级联物理删 biz_paper_question（该卷全部题关系）
            LambdaQueryWrapper<BizPaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
            pqWrapper.eq(BizPaperQuestion::getPaperId, paperId);
            bizPaperQuestionMapper.delete(pqWrapper);

            // 3. 级联物理删 biz_paper_section（该卷全部大题分组）
            LambdaQueryWrapper<BizPaperSection> secWrapper = new LambdaQueryWrapper<>();
            secWrapper.eq(BizPaperSection::getPaperId, paperId);
            bizPaperSectionMapper.delete(secWrapper);

            // 4. 物理删 biz_paper 本体
            bizPaperMapper.deleteById(paperId);
            return null;
        }));
    }
}
