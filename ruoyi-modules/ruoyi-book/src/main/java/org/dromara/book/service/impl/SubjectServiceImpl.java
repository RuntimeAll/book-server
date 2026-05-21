package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.book.domain.vo.SubjectNodeVo;
import org.dromara.book.mapper.BizSubjectMapper;
import org.dromara.book.service.ISubjectService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 章节-知识点树 Service 实现。
 *
 * <p>V0.1 策略：一次性 SELECT * FROM biz_subject + 内存建树。表只 2052 行，一次拉性能 OK。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements ISubjectService {

    private final BizSubjectMapper bizSubjectMapper;

    @Override
    public List<SubjectNodeVo> lazyTree(SubjectLazyTreeBo bo) {
        // V0.1 忽略 bo.parentId（misikt 真实行为也是一次返整树）
        List<BizSubject> all = bizSubjectMapper.selectList(null);
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 实体 → VO（保留 children=null 占位，下一步装配）
        Map<String, SubjectNodeVo> idMap = new HashMap<>(all.size() * 2);
        for (BizSubject e : all) {
            idMap.put(e.getId(), toVo(e));
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

        // 3. sort 排序 + hasChildren 标记（叶子 hasChildren=false，非叶子不带）
        sortRecursive(roots);
        markHasChildren(roots);

        return roots;
    }

    /**
     * BUG-1 修复（V1 PRD §2.4 D-2 调整）：DB 中 363 行 name 是"节点 XXXX"占位字符串
     * （ETL 数据问题，level=2 教材 46 行 + level=3 章 267 行 + 其他 50 行），SQL 层 COALESCE
     * 抓不到（不是 NULL）。BE service 层按 level 兜底前缀（教材 / 章节 / 节 …），保观感。
     */
    private String resolveDisplayName(BizSubject e) {
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

    private SubjectNodeVo toVo(BizSubject e) {
        SubjectNodeVo vo = new SubjectNodeVo();
        vo.setId(e.getId());
        vo.setParentId(e.getParentId());
        String displayName = resolveDisplayName(e);
        vo.setName(displayName);
        vo.setTitle(displayName);           // 兼容字段，FE TS interface 用 title
        vo.setLevel(e.getLevel());
        vo.setSort(e.getSort());
        vo.setKnowledgeImg(e.getKnowledgeImg());
        vo.setKnowledgeVideo(e.getKnowledgeVideo());
        vo.setIsShare(e.getIsShare());      // 已 INT 0/1
        vo.setCreateTime(e.getCreateTime() == null ? null : e.getCreateTime().getTime());  // 毫秒 timestamp
        vo.setKey(e.getId());
        vo.setValue(e.getId());
        vo.setNodeDataSum(null);            // misikt 抓包恒 null
        return vo;
    }

    private void sortRecursive(List<SubjectNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        nodes.sort(Comparator.comparing(SubjectNodeVo::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
        for (SubjectNodeVo n : nodes) {
            sortRecursive(n.getChildren());
        }
    }

    private void markHasChildren(List<SubjectNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        for (SubjectNodeVo n : nodes) {
            if (n.getChildren() == null || n.getChildren().isEmpty()) {
                n.setHasChildren(false);
            }
            // 非叶子节点不显式 set hasChildren（保持跟 misikt 抓包样本一致 — 非叶子无此字段）
            markHasChildren(n.getChildren());
        }
    }
}
