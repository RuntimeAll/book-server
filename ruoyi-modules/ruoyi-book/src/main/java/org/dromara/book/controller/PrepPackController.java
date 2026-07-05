package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.PrepPackBo;
import org.dromara.book.domain.bo.PrepPackRenderBo;
import org.dromara.book.domain.bo.PrepPackSegsBo;
import org.dromara.book.service.schedule.PrepPackService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 备课包 Controller（PRD-C-213 /teacher/schedule/prep-pack）。命中 MisiktEnvelopeAdvice。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/schedule")
@RequiredArgsConstructor
public class PrepPackController {

    private final PrepPackService prepPackService;

    /** 建包（1:1，已存在返已有） → {id}。 */
    @SaCheckLogin
    @PostMapping("/prep-pack")
    public R<Map<String, Object>> build(@RequestBody PrepPackBo bo) {
        Long id = prepPackService.build(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    /** 查（lessonId | sessionId | packId 任一）。 */
    @SaCheckLogin
    @GetMapping("/prep-pack")
    public R<Map<String, Object>> get(@RequestParam(required = false) Long lessonId,
                                     @RequestParam(required = false) Long sessionId,
                                     @RequestParam(required = false) Long packId) {
        return R.ok(prepPackService.get(lessonId, sessionId, packId));
    }

    /** 改段。 */
    @SaCheckLogin
    @PutMapping("/prep-pack/{id}/segs")
    public R<Void> updateSegs(@PathVariable Long id, @RequestBody PrepPackSegsBo bo) {
        prepPackService.updateSegs(id, bo.getSegs());
        return R.ok();
    }

    /** 逐段渲染 PDF → {artifacts}。段无题 → 400 整单不出半卷。 */
    @SaCheckLogin
    @PostMapping("/prep-pack/{id}/render")
    public R<Map<String, Object>> render(@PathVariable Long id, @RequestBody(required = false) PrepPackRenderBo bo) {
        if (bo == null) bo = new PrepPackRenderBo();
        boolean markReady = !Boolean.FALSE.equals(bo.getMarkReady());
        return R.ok(prepPackService.render(id, bo.getSegIndex(), markReady));
    }
}
