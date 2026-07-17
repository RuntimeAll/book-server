package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.service.OralCalcService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 计算题出题器 Controller（口算/横式，人教版 1-6 年级计算谱系）。
 *
 * <p>🔴 必须落 {@code org.dromara.book.controller} 包 —— MisiktEnvelopeAdvice 按 basePackages 装配，
 * /teacher/ 前缀响应转 misikt envelope {code:1,message,response}。
 *
 * <p>路由：
 *   GET  /teacher/oralcalc/types   类型全表 [{code,name,grade,term}]
 *   POST /teacher/oralcalc/export  出卷 {title?, seed?, withGroupLabel?, groups:[{type,count,label?}]}
 *                                  → {questionUrl, answerUrl, total, seed}
 */
@RestController
@RequestMapping("/teacher/oralcalc")
@RequiredArgsConstructor
public class OralCalcController {

    private final OralCalcService oralCalcService;

    @SaCheckLogin
    @GetMapping("/types")
    public R<List<Map<String, Object>>> types() {
        return R.ok(oralCalcService.listTypes());
    }

    @SaCheckLogin
    @PostMapping("/export")
    public R<Map<String, Object>> export(@RequestBody Map<String, Object> body) {
        return R.ok(oralCalcService.export(body));
    }
}
