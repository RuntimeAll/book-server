package org.dromara.book.service;

import org.dromara.book.domain.bo.DnaEditLogBo;
import org.dromara.book.domain.vo.DnaEditLogVo;

import java.util.List;

/**
 * DNA 编辑经验层留痕 Service 接口（PRD-C-100 B4，只写 + 可选 list）。
 *
 * <ul>
 *   <li>POST /teacher/dna-edit-log       → {@link #record}（teacher_id 后端注入）</li>
 *   <li>GET  /teacher/dna-edit-log/list  → {@link #recent}（该 teacher 最近 N 行，调试用）</li>
 * </ul>
 *
 * @author PRD-C-100 B4
 */
public interface IDnaEditLogService {

    /**
     * 写一行留痕（teacher_id 后端注入）。
     *
     * @param teacherId 当前登录老师 id
     * @param bo        入参
     */
    void record(Long teacherId, DnaEditLogBo bo);

    /**
     * 取该老师最近 N 行留痕（按 created_at 倒序）。
     *
     * @param teacherId 当前登录老师 id
     * @param limit     条数（缺省/非法兜底 50）
     * @return {@link DnaEditLogVo} 列表
     */
    List<DnaEditLogVo> recent(Long teacherId, Integer limit);
}
