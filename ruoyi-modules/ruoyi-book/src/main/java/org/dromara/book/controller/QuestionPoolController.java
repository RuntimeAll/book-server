package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.service.schedule.QuestionPoolService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 私有题池检索 Controller（PRD-C-213 /teacher/question/pool）。挂题库域，命中 MisiktEnvelopeAdvice。
 *
 * <p>口径：create_user = 我 且 is_public=0（私有池，版权红线不公开）。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/question")
@RequiredArgsConstructor
public class QuestionPoolController {

    private final QuestionPoolService questionPoolService;

    @SaCheckLogin
    @GetMapping("/pool")
    public R<Map<String, Object>> pool(@RequestParam(required = false) String topicTag,
                                      @RequestParam(required = false) String starLevel,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                      @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return R.ok(questionPoolService.pool(topicTag, starLevel, keyword, pageNum, pageSize));
    }
}
