package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.AutoGenerateBo;
import org.dromara.book.domain.vo.AutoGeneratePaperVo;
import org.dromara.book.service.IAutoPaperService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRD-C-001 — AI 组卷确定性后端 Controller。
 *
 * <p>路径前缀 {@code /teacher/paper}，命中 {@link MisiktEnvelopeAdvice} 自动转 envelope
 * （{@code {code:1, message, response}}）。
 *
 * <p>分层架构后段：入参为 Dify 前段（LLM 语言交互层）锁定真实 subjectId 的结构化大纲，
 * 本接口零 LLM，纯代码确定性查库 + fallback + 组装。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/paper")
@RequiredArgsConstructor
public class AutoPaperController {

    private final IAutoPaperService autoPaperService;

    /**
     * POST /teacher/paper/auto-generate — 按大纲确定性组卷。
     *
     * <p>入参 {@link AutoGenerateBo}：{ title?, outline:[{subjectId, subjectName?, questionType,
     * difficult?, count}], dedup?=true, notesFromLLM? }。
     *
     * <p>出参 {@link AutoGeneratePaperVo}：{ paper:{title, totalCount, sections}, coverage, tips,
     * gaps, notes }。题全部来自真实题库（可溯源 biz_question.id）。
     */
    @SaCheckLogin
    @PostMapping("/auto-generate")
    public R<AutoGeneratePaperVo> autoGenerate(@RequestBody AutoGenerateBo bo) {
        return R.ok(autoPaperService.autoGenerate(bo));
    }
}
