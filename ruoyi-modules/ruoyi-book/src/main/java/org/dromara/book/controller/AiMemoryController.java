package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.AiMemoryBo;
import org.dromara.book.domain.vo.AiMemoryVo;
import org.dromara.book.service.IAiMemoryService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 老师全局 AI 记忆 Controller（PRD-C-100 B4，全 CRUD + toggle 共 5 端点）。
 *
 * <p>路径前缀 {@code /teacher/ai-memory}，命中 {@link MisiktEnvelopeAdvice}
 * 自动包 {@code {code:1, message:"成功", response:<T>}} envelope —— Controller 直接返业务对象 / null。
 *
 * <p>5 端点（全部 {@code @SaCheckLogin}，teacher_id 取 {@code LoginHelper.getUserId()}）：
 * <ul>
 *   <li>GET    /list?memType=&enabledOnly= — 列记忆（memType 可空；enabledOnly 给 toolkit 注入）</li>
 *   <li>POST   /                            — 新增（source 缺省「手填」，Python 自动写传「自动」）</li>
 *   <li>PUT    /{id}                         — 更新（越权返 null 不改）</li>
 *   <li>DELETE /{id}                         — 删除（越权不删）</li>
 *   <li>POST   /{id}/toggle                  — 翻转 enabled（越权返 null）</li>
 * </ul>
 *
 * @author PRD-C-100 B4
 */
@RestController
@RequestMapping("/teacher/ai-memory")
@RequiredArgsConstructor
public class AiMemoryController {

    private final IAiMemoryService aiMemoryService;

    /**
     * GET /teacher/ai-memory/list — 列出当前老师的记忆。
     *
     * @param memType     记忆类型过滤（可空 = 全部）
     * @param enabledOnly true 只返 enabled=1（给 toolkit 注入 prompt），缺省 = 全部（给维护页）
     * @return {@link AiMemoryVo} 列表（按 mem_type, id 排序）
     */
    @SaCheckLogin
    @GetMapping("/list")
    public List<AiMemoryVo> list(@RequestParam(value = "memType", required = false) String memType,
                                 @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        Long teacherId = LoginHelper.getUserId();
        return aiMemoryService.list(teacherId, memType, enabledOnly);
    }

    /**
     * POST /teacher/ai-memory — 新增记忆（teacher_id 后端注入）。
     *
     * @param bo {@code {memType, memKey, memValue, source?, confidence?, remark?}}
     * @return 新增后的 {@link AiMemoryVo}
     */
    @SaCheckLogin
    @PostMapping
    public AiMemoryVo add(@RequestBody AiMemoryBo bo) {
        Long teacherId = LoginHelper.getUserId();
        return aiMemoryService.add(teacherId, bo);
    }

    /**
     * PUT /teacher/ai-memory/{id} — 更新记忆（越权返 null 不改）。
     *
     * @param id 记忆 id
     * @param bo 入参
     * @return 更新后的 {@link AiMemoryVo}，越权返 {@code null}
     */
    @SaCheckLogin
    @PutMapping("/{id}")
    public AiMemoryVo update(@PathVariable("id") Long id, @RequestBody AiMemoryBo bo) {
        Long teacherId = LoginHelper.getUserId();
        return aiMemoryService.update(teacherId, id, bo);
    }

    /**
     * DELETE /teacher/ai-memory/{id} — 删除记忆（越权不删）。
     *
     * @param id 记忆 id
     * @return null（advice 自动转 {@code {code:1, message:"成功", response:null}}）
     */
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public Object remove(@PathVariable("id") Long id) {
        Long teacherId = LoginHelper.getUserId();
        aiMemoryService.remove(teacherId, id);
        return null;
    }

    /**
     * POST /teacher/ai-memory/{id}/toggle — 翻转 enabled（越权返 null）。
     *
     * @param id 记忆 id
     * @return 翻转后的 {@link AiMemoryVo}，越权返 {@code null}
     */
    @SaCheckLogin
    @PostMapping("/{id}/toggle")
    public AiMemoryVo toggle(@PathVariable("id") Long id) {
        Long teacherId = LoginHelper.getUserId();
        return aiMemoryService.toggle(teacherId, id);
    }
}
