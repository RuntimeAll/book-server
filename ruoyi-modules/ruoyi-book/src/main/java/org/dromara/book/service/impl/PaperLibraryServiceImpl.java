package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.PaperLazyTreeBo;
import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.entity.BizPaperCategory;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.PaperCategoryNodeVo;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.service.IPaperLibraryService;
import org.springframework.stereotype.Service;

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
 *   <li>isShare 字段口径：DB TINYINT → 响应 STRING '0'/'1'（misikt 风格）</li>
 *   <li>3001 根节点 parentId override 为 "1"（misikt 真响应历史遗留 bug，字节级对齐）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class PaperLibraryServiceImpl implements IPaperLibraryService {

    private final BizPaperCategoryMapper bizPaperCategoryMapper;
    private final BizPaperMapper bizPaperMapper;

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
     * isShare INT → STRING '0'/'1'；3001 根节点 parentId override '1'。
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
        // isShare DB TINYINT → STRING '0'/'1'（misikt 风格）
        vo.setIsShare(e.getIsShare() == null ? "0" : String.valueOf(e.getIsShare()));
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

        QueryWrapper<PaperListItemVo> wrapper = new QueryWrapper<>();
        wrapper.eq("p.status", "1");

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
}
