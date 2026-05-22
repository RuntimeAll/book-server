package org.dromara.book.service;

import org.dromara.book.domain.vo.PaperDetailVo;

/**
 * 试卷详情 Service 接口（E 卡段② — POST /teacher/exam/paper/detail）。
 *
 * <p>业务：给定 paperId，返该卷的"卷头完整字段 + 按大题分组的题列表"。
 *
 * <p>跟 {@link IPaperSourceService}（原卷预览，扁平 questions[]）的区别：
 * 本接口按 biz_paper_section 分组，每个 section 内含 questions，对应试卷详情独立页的展示需求。
 *
 * <p>边界：
 * <ul>
 *   <li>paperId 为 null → 返 null（防御）</li>
 *   <li>paper 不存在 / status≠'1' → 返 null（advice 包 envelope 输出 null）</li>
 *   <li>paper 存在但 0 section → 返 PaperDetailVo with sections=[]</li>
 *   <li>section 存在但 0 题 → 该 section 的 questions=[]</li>
 * </ul>
 *
 * @author backend-dev (E 卡段②)
 */
public interface IPaperDetailService {

    /**
     * POST /teacher/exam/paper/detail — 试卷详情。
     *
     * @param paperId 试卷 id
     * @return 卷头 + 按大题分组的题列表；不存在 / 软删 / 草稿 → null
     */
    PaperDetailVo getPaperDetail(Long paperId);
}
