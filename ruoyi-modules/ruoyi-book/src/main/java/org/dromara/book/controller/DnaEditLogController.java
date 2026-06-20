package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.DnaEditLogBo;
import org.dromara.book.domain.vo.DnaEditLogVo;
import org.dromara.book.service.IDnaEditLogService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DNA 编辑经验层留痕 Controller（PRD-C-100 B4，只写 + 可选 list 共 2 端点）。
 *
 * <p>路径前缀 {@code /teacher/dna-edit-log}，命中 {@link MisiktEnvelopeAdvice}
 * 自动包 {@code {code:1, message:"成功", response:<T>}} envelope —— Controller 直接返业务对象 / null。
 *
 * <p>2 端点（全部 {@code @SaCheckLogin}，teacher_id 取 {@code LoginHelper.getUserId()}）：
 * <ul>
 *   <li>POST /                  — 写一行留痕（teacher_id 后端注入）→ null</li>
 *   <li>GET  /list?limit=50     — 该 teacher 最近 N 行（调试用）</li>
 * </ul>
 *
 * @author PRD-C-100 B4
 */
@RestController
@RequestMapping("/teacher/dna-edit-log")
@RequiredArgsConstructor
public class DnaEditLogController {

    private final IDnaEditLogService dnaEditLogService;

    /**
     * POST /teacher/dna-edit-log — 写一行留痕。
     *
     * @param bo {@code {targetId?, editKind, dim?, before?, after?, correctionPrompt?, committed?}}
     * @return null（advice 自动转 {@code {code:1, message:"成功", response:null}}）
     */
    @SaCheckLogin
    @PostMapping
    public Object record(@RequestBody DnaEditLogBo bo) {
        Long teacherId = LoginHelper.getUserId();
        dnaEditLogService.record(teacherId, bo);
        return null;
    }

    /**
     * GET /teacher/dna-edit-log/list — 该老师最近 N 行留痕（调试用）。
     *
     * @param limit 条数（缺省 50）
     * @return {@link DnaEditLogVo} 列表
     */
    @SaCheckLogin
    @GetMapping("/list")
    public List<DnaEditLogVo> list(@RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        Long teacherId = LoginHelper.getUserId();
        return dnaEditLogService.recent(teacherId, limit);
    }
}
