package org.dromara.book.service;

import org.dromara.book.domain.vo.PaperSourceVo;

/**
 * 原卷预览 Service 接口（PRD §3.1 B-10 — GET /teacher/paper/source/{id}）。
 *
 * <p>业务：给定 paperId，返该卷的"卷头信息（id/name/examYear）+ 该卷下所有已发布题"
 * （按 sort 升序）。
 *
 * <p>边界：
 * <ul>
 *   <li>paperId 为 null → 返 null（防御）</li>
 *   <li>paper 不存在 / status≠'1' → 返 null（advice 包 envelope 输出 null）</li>
 *   <li>paper 存在但 0 题 → 返 PaperSourceVo with questions=[]（不返 null）</li>
 * </ul>
 *
 * @author backend-dev
 */
public interface IPaperSourceService {

    /**
     * GET /teacher/paper/source/{id} — 原卷预览。
     *
     * @param paperId 试卷 id
     * @return 卷头 + 卷下所有题；不存在 / 软删 / 草稿 → null
     */
    PaperSourceVo getPaperSource(Long paperId);
}
