package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
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
import org.dromara.book.service.IPaperDetailService;
import org.dromara.book.service.IPaperLibraryService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
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
 * <p>🔴 PRD-B-013 减法：共享标记列已 DROP（biz_paper / biz_paper_category），原 public 分支/toVo 内相关
 * 逻辑同步清除；公共卷库归属唯独靠 subject_id 分类前缀（3001/3003/3004）。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class PaperLibraryServiceImpl implements IPaperLibraryService {

    private final BizPaperCategoryMapper bizPaperCategoryMapper;
    private final BizPaperMapper bizPaperMapper;
    private final BizPaperSectionMapper bizPaperSectionMapper;
    private final BizPaperQuestionMapper bizPaperQuestionMapper;
    private final IPaperDetailService paperDetailService;

    /** Q 卡默认 section title — FE 不展示，仅满足 biz_paper_question.section_id NOT NULL 约束 */
    private static final String DEFAULT_SECTION_TITLE = "题目";

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
            ? DEFAULT_PAGE_SIZE : bo.getPageSize();

        // 数据隔离：取当前登录用户 userId（create_by 存数字字符串）
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录用户不能访问卷库");
        }
        String currentUserIdStr = String.valueOf(currentUserId);

        QueryWrapper<PaperListItemVo> wrapper = new QueryWrapper<>();
        wrapper.eq("p.status", "1");

        // scope 分流：'mine' → 只看自己创建的；其余（'public' / 缺省）→ 公共卷库，按分类树归属过滤
        // 🔴 PRD-B-013: 共享标记列已 DROP；公共卷库语义 = 按 paper_category 分类树（subject_id 前缀）。
        if ("mine".equals(bo.getScope())) {
            // 我的卷库：只看当前登录用户自己创建的，绝不信任前端传的 createBy
            wrapper.eq("p.create_by", currentUserIdStr);
        }
        // else 公共卷库：不加 create_by 归属过滤，靠下方 subject_id 分类前缀筛

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
        wrapper.orderByDesc("p.sort");

        Page<PaperListItemVo> mpPage = new Page<>(pageIndex, pageSize);
        IPage<PaperListItemVo> result = bizPaperMapper.selectPaperListPage(mpPage, wrapper);
        return MisiktPageVo.of(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateExamPaperVo createExamPaper(CreateExamPaperBo bo) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录用户不能创建试卷");
        }
        if (bo.getQuestionIds() == null || bo.getQuestionIds().isEmpty()) {
            throw new ServiceException("题目列表不能为空");
        }

        int qCount = bo.getQuestionIds().size();
        Date now = new Date();
        String userIdStr = String.valueOf(currentUserId);

        // 1. INSERT biz_paper
        BizPaper paper = new BizPaper();
        paper.setName(bo.getName());
        paper.setPaperCategoryId(bo.getPaperCategoryId());
        paper.setQuestionCount(qCount);
        paper.setScore(BigDecimal.ZERO);
        paper.setPaperType(1);
        paper.setStatus("1");
        paper.setSort(0);
        paper.setCreateBy(userIdStr);
        paper.setCreateTime(now);
        paper.setUpdateBy(userIdStr);
        paper.setUpdateTime(now);
        bizPaperMapper.insert(paper);
        Long newPaperId = paper.getId();

        // 2. INSERT biz_paper_section（默认 1 个分组，所有题挂下面）
        BizPaperSection section = new BizPaperSection();
        section.setPaperId(newPaperId);
        section.setTitle(DEFAULT_SECTION_TITLE);
        section.setSort(1);
        bizPaperSectionMapper.insert(section);
        Long newSectionId = section.getId();

        // 3. 批量 INSERT biz_paper_question（sort 按试题栏 LS 顺序 1/2/3...）
        List<BizPaperQuestion> pqList = new ArrayList<>(qCount);
        for (int i = 0; i < qCount; i++) {
            BizPaperQuestion pq = new BizPaperQuestion();
            pq.setPaperId(newPaperId);
            pq.setSectionId(newSectionId);
            pq.setQuestionId(bo.getQuestionIds().get(i));
            pq.setSort(i + 1);
            pq.setScore(BigDecimal.ZERO);
            pqList.add(pq);
        }
        bizPaperQuestionMapper.insertBatch(pqList);

        return new CreateExamPaperVo(newPaperId, qCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperDetailVo updateExamPaper(UpdateExamPaperBo bo) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录用户不能编辑试卷");
        }
        if (bo.getPaperId() == null) {
            throw new ServiceException("试卷ID不能为空");
        }
        if (bo.getQuestions() == null || bo.getQuestions().isEmpty()) {
            throw new ServiceException("题目列表不能为空");
        }

        Long paperId = bo.getPaperId();

        // biz_paper / biz_paper_question 走手动 wrapper 隔离、无 @DataPermission 注解，
        // 注解式数据权限拦截器不会注入 AND create_by=登录id；此处 ignore 包裹作防御（无注解时为 no-op），
        // 规避 PRD-A-002 沉淀的「数据权限拦截致写 0 行静默假成功」坑。
        //
        // 🔴 再叠一层 TenantHelper.ignore（PRD-A-005 G4 修复）：BaseMapper 继承方法
        // selectById / updateById 的 mappedStatementId namespace 落在 BaseMapper（非
        // BizPaperMapper），故 BizPaperMapper 类级 @InterceptorIgnore(tenantLine) 命中不到
        // 这两个继承方法 → 多租户拦截器仍对 biz_paper 注入 AND tenant_id=? → 该表无此列报
        // SQLSyntaxErrorException 致整事务回滚。TenantHelper.ignore 走线程级 ThreadLocal
        // IgnoreStrategy，在 SQL 执行时刻判断、对继承方法同样生效，补上类级注解盲区。
        // 与 DataPermissionHelper.ignore 写 IgnoreStrategy 不同字段、可叠加、互不覆盖。
        // 覆盖事务全链路：selectById 校验存在 + delete + insertBatch + updateById 重算。
        return TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // 1. 校验 paperId 存在
            BizPaper existing = bizPaperMapper.selectById(paperId);
            if (existing == null) {
                throw new ServiceException("试卷不存在: " + paperId);
            }

            // 1.1 owner 校验（PRD-A-005 收尾 C-权限锁死）：只能编辑本人创建的卷，公共卷/他人卷一律拒绝。
            //     绝不信任前端，归属一律以 create_by vs LoginHelper.getUserId() 为准。
            if (!String.valueOf(currentUserId).equals(existing.getCreateBy())) {
                throw new ServiceException("无权编辑非本人创建的试卷");
            }

            Date now = new Date();
            String userIdStr = String.valueOf(currentUserId);

            // 2. 删该 paperId 旧 biz_paper_question 全部行
            LambdaQueryWrapper<BizPaperQuestion> delWrapper = new LambdaQueryWrapper<>();
            delWrapper.eq(BizPaperQuestion::getPaperId, paperId);
            bizPaperQuestionMapper.delete(delWrapper);

            // 3. 按 questions 批量重插（section_id / question_id / sort / score）
            List<UpdateExamPaperBo.UpdateExamPaperQuestionBo> items = bo.getQuestions();
            BigDecimal totalScore = BigDecimal.ZERO;
            List<BizPaperQuestion> pqList = new ArrayList<>(items.size());
            for (UpdateExamPaperBo.UpdateExamPaperQuestionBo item : items) {
                BizPaperQuestion pq = new BizPaperQuestion();
                pq.setPaperId(paperId);
                pq.setSectionId(item.getSectionId());
                pq.setQuestionId(item.getQuestionId());
                pq.setSort(item.getSort());
                BigDecimal score = item.getScore() == null ? BigDecimal.ZERO : item.getScore();
                pq.setScore(score);
                totalScore = totalScore.add(score);
                pqList.add(pq);
            }
            bizPaperQuestionMapper.insertBatch(pqList);

            // 4. 重算并更新 biz_paper 的 question_count + 总 score（+ name / paperCategoryId / suggestTime 如传）
            BizPaper update = new BizPaper();
            update.setId(paperId);
            update.setQuestionCount(items.size());
            update.setScore(totalScore);
            if (bo.getName() != null) {
                update.setName(bo.getName());
            }
            if (bo.getPaperCategoryId() != null) {
                update.setPaperCategoryId(bo.getPaperCategoryId());
            }
            // PRD-A-007 T2：suggestTime 可选，传则写 biz_paper.suggest_time
            if (bo.getSuggestTime() != null) {
                update.setSuggestTime(bo.getSuggestTime());
            }
            update.setUpdateBy(userIdStr);
            update.setUpdateTime(now);
            bizPaperMapper.updateById(update);

            // PRD-A-007 T2：sections 可选 — 逐条更新已有 biz_paper_section 的 title/sort
            // sectionId 非空才更新（空 = 新建，v1 本期不做）
            if (bo.getSections() != null && !bo.getSections().isEmpty()) {
                for (UpdateExamPaperBo.SectionBo sectionBo : bo.getSections()) {
                    if (sectionBo.getSectionId() == null) {
                        // 新建大题 v1 不做，跳过
                        continue;
                    }
                    BizPaperSection sectionUpdate = new BizPaperSection();
                    sectionUpdate.setId(sectionBo.getSectionId());
                    if (sectionBo.getName() != null) {
                        sectionUpdate.setTitle(sectionBo.getName());
                    }
                    if (sectionBo.getSort() != null) {
                        sectionUpdate.setSort(sectionBo.getSort());
                    }
                    bizPaperSectionMapper.updateById(sectionUpdate);
                }
            }

            // 5. 返更新后 PaperDetailVo（复用 detail 查询）
            return paperDetailService.getPaperDetail(paperId);
        }));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExamPaper(Long paperId) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录用户不能删除试卷");
        }
        if (paperId == null) {
            throw new ServiceException("试卷ID不能为空");
        }

        // biz_paper / biz_paper_question / biz_paper_section 三表均无 tenant_id 列，
        // BaseMapper 继承方法（selectById / deleteById / delete 带 WHERE）会被多租户拦截器
        // 注入 AND tenant_id=? 报 Unknown column 'tenant_id'（PRD-A-005 G4 已踩）。
        // 故全事务体走 TenantHelper.ignore(DataPermissionHelper.ignore(...)) 线程级包裹。
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // 1. owner 校验：只能删本人创建的卷，公共卷/他人卷一律拒绝
            BizPaper existing = bizPaperMapper.selectById(paperId);
            if (existing == null) {
                throw new ServiceException("试卷不存在: " + paperId);
            }
            if (!String.valueOf(currentUserId).equals(existing.getCreateBy())) {
                throw new ServiceException("无权删除非本人创建的试卷");
            }

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
