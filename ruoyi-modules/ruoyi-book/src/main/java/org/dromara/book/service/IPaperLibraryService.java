package org.dromara.book.service;

import org.dromara.book.domain.bo.PaperLazyTreeBo;
import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.PaperCategoryNodeVo;
import org.dromara.book.domain.vo.PaperListItemVo;

import java.util.List;

/**
 * 卷库 Service 接口（D 卡 V0.5 卷库视觉级还原）。
 *
 * <p>承担 2 个 misikt 风格端点：
 * <ul>
 *   <li>POST /teacher/exam/paper/lazyTree — 试卷分类树（97 节点 / 3 根）</li>
 *   <li>POST /teacher/exam/paper/page — 试卷分页列表（misikt PageHelper 完整结构）</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface IPaperLibraryService {

    /**
     * POST /teacher/exam/paper/lazyTree — 拉试卷分类树。
     *
     * <p>策略：一次性 SELECT * FROM biz_paper_category WHERE parent_id NOT LIKE '-%' + 内存建树。
     * 表只 97 行（+6 deprecated 软删行），一次拉无性能压力。
     *
     * <p>入参 bo.type / bo.version V0.5 BE 忽略（misikt 真站固定值，我们仅有"浙教新版"）。
     *
     * @param bo 入参 BO（type=2 + version=1010，V0.5 忽略）
     * @return 树（含 3 根：3001 公共试卷 / 3003 资料库 / 3004 专题卷库）
     */
    List<PaperCategoryNodeVo> lazyTree(PaperLazyTreeBo bo);

    /**
     * POST /teacher/exam/paper/page — 分页拉试卷列表。
     *
     * <p>过滤：status='1' / name LIKE %name% / subject_id LIKE 'subjectId%'，
     * 排序：sort DESC。响应 PageHelper 完整结构（misikt 真站对齐）。
     *
     * @param bo 分页 + 筛选入参（misikt 风格 pageIndex / subjectId / name）
     * @return misikt 风格分页 VO
     */
    MisiktPageVo<PaperListItemVo> page(PaperPageBo bo);
}
