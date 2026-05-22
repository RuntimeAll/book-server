package org.dromara.bookadmin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.book.domain.vo.FreeTagVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.book.domain.vo.SubjectNodeVo;
import org.dromara.book.mapper.BizQuestionFreeTagMapper;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizSubjectMapper;
import org.dromara.bookadmin.service.IAdminQuestionService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * admin 题目 Service 实现（H1 卡 V-6 — 写操作统一事务入口）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本实现物理落 {@code ruoyi-book-admin} 模块，
 * 依赖 {@code ruoyi-book} 共享 Mapper / Entity / VO（DDL 单源），但方法内部
 * <strong>直接走 Mapper 自查</strong>，<strong>禁调</strong>
 * {@code org.dromara.book.service.IQuestionService}（教师端 Service）的任何方法。
 *
 * <p>本波（V-5 重构）{@link #adminPage(QuestionPageBo)} 实现策略与教师端
 * {@code QuestionServiceImpl#page(QuestionPageBo)} 当前等效（同 SQL 同回填），
 * 但独立维护 — admin 后续可加 status='0' 草稿过滤 / 创建人过滤 / status='2' 软删可见
 * 等需求，改本方法不影响 teacher 的 page。
 *
 * <p>下波（V-1/V-2/V-3/V-4）在此接管：
 * <ul>
 *   <li>事务边界（@Transactional 写操作必加）</li>
 *   <li>biz_question / biz_question_knowledge（U 轨全量替换）/ biz_question_free_tag
 *       + biz_free_tag 字典 use_count 同步 / biz_question.free_tag 冗余串同步</li>
 *   <li>软删时 biz_paper_question 引用校验（抛 ServiceException）</li>
 *   <li>fileUpload 走 C 卡 OssClient + 写 image_asset（source='admin'）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class AdminQuestionServiceImpl implements IAdminQuestionService {

    private final BizQuestionMapper bizQuestionMapper;
    private final BizQuestionKnowledgeMapper bizQuestionKnowledgeMapper;
    private final BizQuestionFreeTagMapper bizQuestionFreeTagMapper;
    private final BizSubjectMapper bizSubjectMapper;

    /** misikt 默认每页 10，pageIndex 兜底 1（与教师端一致）。 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_INDEX = 1;

    @Override
    public MisiktPageVo<QuestionItemVo> adminPage(QuestionPageBo bo) {
        int pageIndex = bo.getPageIndex() == null || bo.getPageIndex() <= 0
            ? DEFAULT_PAGE_INDEX : bo.getPageIndex();
        int pageSize = bo.getPageSize() == null || bo.getPageSize() <= 0
            ? DEFAULT_PAGE_SIZE : bo.getPageSize();

        QueryWrapper<BizQuestion> wrapper = buildAdminPageWrapper(bo);

        // 兼容 J 卡段② mapper 签名（另一线程已 commit）：第 3 参数 currentUserId 用于
        // LEFT JOIN biz_question_favorite 出 isFavorite。admin 通道也走 Sa-Token 登录态。
        Long currentUserId = LoginHelper.getUserId();

        Page<QuestionItemVo> mpPage = new Page<>(pageIndex, pageSize);
        IPage<QuestionItemVo> result = bizQuestionMapper.selectQuestionPage(mpPage, wrapper, currentUserId);

        // 回填 questionKnowledges（source='U'） + freeTags（与教师端等效）
        List<QuestionItemVo> records = result.getRecords();
        if (records != null && !records.isEmpty()) {
            List<Long> ids = records.stream().map(QuestionItemVo::getId).collect(Collectors.toList());
            Map<Long, List<QuestionKnowledgeVo>> uMap = loadKnowledgesByQuestionIds(ids, "U");
            Map<Long, List<FreeTagVo>> ftMap = loadFreeTagsByQuestionIds(ids);
            for (QuestionItemVo vo : records) {
                vo.setQuestionKnowledges(uMap.getOrDefault(vo.getId(), Collections.emptyList()));
                vo.setFreeTags(ftMap.getOrDefault(vo.getId(), Collections.emptyList()));
            }
        }

        return MisiktPageVo.of(result);
    }

    /**
     * 构造 admin page 查询 WHERE 条件（独立维护，当前与教师端 buildPageWrapper 等效）。
     *
     * <p>本波（V-5 重构）admin 复用教师端默认行为：
     * <ul>
     *   <li>{@code status='1'} 硬编码（暂只返已发布题；下波 V-2/V-3 接管时放开此限制以便 admin 看草稿/软删）</li>
     *   <li>subjectId 空或 "0" 不过滤；非数字串直接 1=0 兜底</li>
     *   <li>{@code notUsedQuestion=1} 加 NOT IN biz_paper_question 子查询</li>
     * </ul>
     */
    private QueryWrapper<BizQuestion> buildAdminPageWrapper(QuestionPageBo bo) {
        QueryWrapper<BizQuestion> w = new QueryWrapper<>();
        // 本波保持与 teacher 一致只看 status='1'；admin 下波将开放 status 入参支持
        w.eq("status", "1");

        if (bo.getSubjectId() != null && !bo.getSubjectId().isEmpty() && !"0".equals(bo.getSubjectId())) {
            // 题↔章节关联走 biz_question_knowledge.knowledge_id（教师端 BUG-2 修复，
            // 数据建模/07-补充资料/W-6-章节树数据复刻方案-2026-05-21.md §4.1）
            String sid = bo.getSubjectId();
            if (!sid.matches("^\\d+$")) {
                w.apply("1=0");
            } else {
                w.inSql("id",
                    "SELECT DISTINCT question_id FROM biz_question_knowledge "
                        + "WHERE knowledge_id LIKE '" + sid + "%'");
            }
        }
        if (bo.getQuestionType() != null) {
            w.eq("question_type", bo.getQuestionType());
        }
        if (bo.getDifficult() != null) {
            w.eq("difficult", bo.getDifficult());
        }
        if (bo.getKeyWord() != null && !bo.getKeyWord().isEmpty()) {
            w.like("stem_text", bo.getKeyWord());
        }
        if (bo.getNotUsedQuestion() != null && bo.getNotUsedQuestion() == 1) {
            w.notInSql("id", "SELECT question_id FROM biz_paper_question");
        }
        // mapper.xml selectQuestionPage 走 LEFT JOIN biz_question_favorite，主表 alias=q，
        // favorite 表也有 create_time / id 列 → orderBy 必须加 q. 前缀避免 ambiguous。
        w.orderByDesc("q.create_time").orderByDesc("q.id");
        return w;
    }

    @Override
    public List<SubjectNodeVo> adminLazyTree(SubjectLazyTreeBo bo) {
        // V0.1 忽略 bo.parentId（与教师端 SubjectServiceImpl 等效，misikt 真实行为也是一次返整树）
        List<BizSubject> all = bizSubjectMapper.selectList(null);
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 实体 → VO
        Map<String, SubjectNodeVo> idMap = new HashMap<>(all.size() * 2);
        for (BizSubject e : all) {
            idMap.put(e.getId(), toSubjectVo(e));
        }

        // 2. 串父子关系；parent 不在结果集中的视为顶层
        List<SubjectNodeVo> roots = new ArrayList<>();
        for (BizSubject e : all) {
            SubjectNodeVo node = idMap.get(e.getId());
            SubjectNodeVo parent = e.getParentId() == null ? null : idMap.get(e.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }

        // 3. sort 排序 + hasChildren 标记
        sortSubjectRecursive(roots);
        markSubjectHasChildren(roots);

        return roots;
    }

    /**
     * BUG-1 兜底（W-6 数据已大幅清洗，但保留以防新增"节点 XXXX"漏网）。
     */
    private String resolveSubjectName(BizSubject e) {
        String name = e.getName();
        if (name == null || !name.matches("^节点 \\d+$")) {
            return name;
        }
        Integer level = e.getLevel();
        String prefix = level == null ? "节点 " : switch (level) {
            case 1 -> "学科 ";
            case 2 -> "教材 ";
            case 3 -> "章节 ";
            case 4 -> "节 ";
            case 5 -> "知识点 ";
            default -> "节点 ";
        };
        return prefix + e.getId();
    }

    private SubjectNodeVo toSubjectVo(BizSubject e) {
        SubjectNodeVo vo = new SubjectNodeVo();
        vo.setId(e.getId());
        vo.setParentId(e.getParentId());
        String displayName = resolveSubjectName(e);
        vo.setName(displayName);
        vo.setTitle(displayName);
        vo.setLevel(e.getLevel());
        vo.setSort(e.getSort());
        vo.setKnowledgeImg(e.getKnowledgeImg());
        vo.setKnowledgeVideo(e.getKnowledgeVideo());
        vo.setIsShare(e.getIsShare());
        vo.setCreateTime(e.getCreateTime() == null ? null : e.getCreateTime().getTime());
        vo.setKey(e.getId());
        vo.setValue(e.getId());
        vo.setNodeDataSum(null);
        return vo;
    }

    private void sortSubjectRecursive(List<SubjectNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        nodes.sort(Comparator.comparing(SubjectNodeVo::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
        for (SubjectNodeVo n : nodes) {
            sortSubjectRecursive(n.getChildren());
        }
    }

    private void markSubjectHasChildren(List<SubjectNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        for (SubjectNodeVo n : nodes) {
            if (n.getChildren() == null || n.getChildren().isEmpty()) {
                n.setHasChildren(false);
            }
            markSubjectHasChildren(n.getChildren());
        }
    }

    /**
     * 批量按 question_id + source 拉 knowledges 并按 questionId 分组。
     *
     * @param questionIds 题目 ID 集合（空集合返空 Map）
     * @param source      'U' 或 'S'
     */
    private Map<Long, List<QuestionKnowledgeVo>> loadKnowledgesByQuestionIds(Collection<Long> questionIds,
                                                                              String source) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<QuestionKnowledgeVo> all = bizQuestionKnowledgeMapper.selectByQuestionIdsAndSource(questionIds, source);
        if (all == null || all.isEmpty()) {
            return Collections.emptyMap();
        }
        return all.stream().collect(Collectors.groupingBy(QuestionKnowledgeVo::getQuestionId));
    }

    /**
     * 批量按 question_id 拉 freeTags 并按 questionId 分组（X 卡段②）。
     *
     * <p>Mapper 返回 {@link BizQuestionFreeTagMapper.FreeTagWithQid} 含 questionId，
     * 本方法复制为纯 {@link FreeTagVo}（避免 Jackson 序列化时把 questionId 写进响应 JSON）。
     *
     * @param questionIds 题目 ID 集合（空集合返空 Map）
     */
    private Map<Long, List<FreeTagVo>> loadFreeTagsByQuestionIds(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BizQuestionFreeTagMapper.FreeTagWithQid> all =
            bizQuestionFreeTagMapper.selectGroupedByQuestionIds(questionIds);
        if (all == null || all.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<FreeTagVo>> grouped = new HashMap<>();
        for (BizQuestionFreeTagMapper.FreeTagWithQid row : all) {
            FreeTagVo pure = new FreeTagVo();
            pure.setId(row.getId());
            pure.setName(row.getName());
            pure.setPosition(row.getPosition());
            grouped.computeIfAbsent(row.getQuestionId(), k -> new ArrayList<>()).add(pure);
        }
        return grouped;
    }
}
