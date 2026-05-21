package org.dromara.book.service;

import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.vo.SubjectNodeVo;

import java.util.List;

/**
 * 章节-知识点树 Service。
 *
 * @author backend-dev
 */
public interface ISubjectService {

    /**
     * 拉章节树（misikt /teacher/question/lazyTree 对齐）。
     *
     * <p>V0.1 行为：忽略 bo.parentId，一次性查全表 + 内存建树返根（含整棵嵌套 children）。
     * 跟 misikt 抓包真实行为一致（misikt 表面上是懒加载入参，实际首次调用就返整树）。
     *
     * @param bo 入参（parentId V0.1 忽略）
     * @return 嵌套树顶层节点列表（V0.1 数据：1 个学科根 3071 + 8 册 children + 多层叶子，共 2052 节点）
     */
    List<SubjectNodeVo> lazyTree(SubjectLazyTreeBo bo);
}
