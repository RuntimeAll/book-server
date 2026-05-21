package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.vo.SubjectNodeVo;
import org.dromara.book.service.ISubjectService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 章节-知识点树 Controller。
 *
 * <p>路径前缀对齐 misikt：/teacher/question/lazyTree
 * （根据子 PRD §4 端点契约，B 模块章节树挂在 question/ 下，不挂 subject/）。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/question")
@RequiredArgsConstructor
public class SubjectController {

    private final ISubjectService subjectService;

    /**
     * POST /teacher/question/lazyTree — 拉章节-知识点树。
     *
     * <p>入参 parentId V0.1 忽略（misikt 真实行为也是一次返整树）；
     * 入参 body 即便缺省也允许（@RequestBody(required = false)）— 给 FE
     * 不传 body 的边界场景兜底。
     */
    @SaCheckLogin
    @PostMapping("/lazyTree")
    public R<List<SubjectNodeVo>> lazyTree(@RequestBody(required = false) SubjectLazyTreeBo bo) {
        if (bo == null) {
            bo = new SubjectLazyTreeBo();
        }
        return R.ok(subjectService.lazyTree(bo));
    }
}
