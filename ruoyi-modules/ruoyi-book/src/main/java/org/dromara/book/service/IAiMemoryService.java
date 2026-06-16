package org.dromara.book.service;

import org.dromara.book.domain.bo.AiMemoryBo;
import org.dromara.book.domain.vo.AiMemoryVo;

import java.util.List;

/**
 * 老师全局 AI 记忆 Service 接口（PRD-C-100 B4）。
 *
 * <p>全 CRUD + toggle，全部按 teacher_id 隔离（越权校验在写/删/翻转时强制）：
 * <ul>
 *   <li>GET    /teacher/ai-memory/list        → {@link #list}（memType 可空；enabledOnly 给 toolkit 注入）</li>
 *   <li>POST   /teacher/ai-memory             → {@link #add}（source 缺省「手填」）</li>
 *   <li>PUT    /teacher/ai-memory/{id}        → {@link #update}（越权返 null 不改）</li>
 *   <li>DELETE /teacher/ai-memory/{id}        → {@link #remove}（越权不删）</li>
 *   <li>POST   /teacher/ai-memory/{id}/toggle → {@link #toggle}（越权返 null）</li>
 * </ul>
 *
 * @author PRD-C-100 B4
 */
public interface IAiMemoryService {

    /**
     * 列出某老师的记忆。
     *
     * @param teacherId   当前登录老师 id
     * @param memType     记忆类型过滤（null/空 = 全部）
     * @param enabledOnly true 只返 enabled=1（给 toolkit 注入），false/null = 全部（给维护页）
     * @return 按 mem_type, id 排序的 {@link AiMemoryVo} 列表
     */
    List<AiMemoryVo> list(Long teacherId, String memType, Boolean enabledOnly);

    /**
     * 新增记忆（teacher_id 后端注入，source 缺省「手填」）。
     *
     * @param teacherId 当前登录老师 id
     * @param bo        入参
     * @return 新增后的 {@link AiMemoryVo}
     */
    AiMemoryVo add(Long teacherId, AiMemoryBo bo);

    /**
     * 更新记忆（校验该 id 的 teacher_id == 当前登录老师，越权返 null 不改）。
     *
     * @param teacherId 当前登录老师 id
     * @param id        记忆 id
     * @param bo        入参
     * @return 更新后的 {@link AiMemoryVo}，越权返 {@code null}
     */
    AiMemoryVo update(Long teacherId, Long id, AiMemoryBo bo);

    /**
     * 删除记忆（同越权校验）。
     *
     * @param teacherId 当前登录老师 id
     * @param id        记忆 id
     */
    void remove(Long teacherId, Long id);

    /**
     * 翻转 enabled（同越权校验）。
     *
     * @param teacherId 当前登录老师 id
     * @param id        记忆 id
     * @return 翻转后的 {@link AiMemoryVo}，越权返 {@code null}
     */
    AiMemoryVo toggle(Long teacherId, Long id);
}
