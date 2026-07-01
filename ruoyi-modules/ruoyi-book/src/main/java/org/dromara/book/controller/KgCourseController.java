package org.dromara.book.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.CourseKgVo;
import org.dromara.book.service.ICourseKgService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课时知识梳理讲义 只读接口 —— 供前端 1:1 还原教辅讲义页。
 *
 * <p>前缀 {@code /teacher/kg/**}，命中 {@link MisiktEnvelopeAdvice} 自动包
 * {@code {code:1, message:"成功", response:<CourseKgVo>}}，前端按 code===1 判成功。
 *
 * <p>🔴 免登录（演示用）—— 已在 application.yml security.excludes 放行；纯只读，不写库。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/kg")
@RequiredArgsConstructor
public class KgCourseController {

    private final ICourseKgService courseKgService;

    /**
     * GET /teacher/kg/course/{courseId} — 返回一个课时（level4）的完整知识梳理讲义。
     *
     * @param courseId 课时节点 id，如 901001002001
     */
    @GetMapping("/course/{courseId}")
    public R<CourseKgVo> course(@PathVariable("courseId") String courseId) {
        return R.ok(courseKgService.getCourseKg(courseId));
    }
}
