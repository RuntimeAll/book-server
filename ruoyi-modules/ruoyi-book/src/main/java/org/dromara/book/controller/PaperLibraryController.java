package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.PaperLazyTreeBo;
import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.PaperCategoryNodeVo;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.service.IPaperLibraryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 卷库 Controller（D 卡 V0.5 卷库视觉级还原）。
 *
 * <p>路径前缀 {@code /teacher/exam/paper}，命中 {@link MisiktEnvelopeAdvice} 自动转 envelope
 * {@code {code:1, message:"成功", response:...}}。
 *
 * <p>⚠️ 关键：本 Controller 走裸返回（不走 R&lt;T&gt;）—— advice 裸返回路径 message 用 "成功"
 * 对齐 misikt 真响应字节级；R&lt;T&gt; 路径 message 用 "操作成功"（V1 兼容，回归测试断言依赖）。
 *
 * <p>2 个端点：
 * <ul>
 *   <li>POST /teacher/exam/paper/lazyTree — 试卷分类树（97 节点 / 3 根）</li>
 *   <li>POST /teacher/exam/paper/page — 试卷分页列表（misikt PageHelper 完整结构）</li>
 * </ul>
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/exam/paper")
@RequiredArgsConstructor
public class PaperLibraryController {

    private final IPaperLibraryService paperLibraryService;

    /**
     * POST /teacher/exam/paper/lazyTree — 试卷分类树。
     *
     * <p>入参 type=2 / version=1010 V0.5 BE 忽略（misikt 真站固定值）。
     * 入参 body 缺省时 BO 也允许 null — 走 V0.5 兜底（全量返树）。
     *
     * <p>裸返回 List —— advice 自动包 envelope，message 走 "成功"（misikt 风格）。
     */
    @SaCheckLogin
    @PostMapping("/lazyTree")
    public List<PaperCategoryNodeVo> lazyTree(@RequestBody(required = false) PaperLazyTreeBo bo) {
        if (bo == null) {
            bo = new PaperLazyTreeBo();
        }
        return paperLibraryService.lazyTree(bo);
    }

    /**
     * POST /teacher/exam/paper/page — 试卷分页列表。
     *
     * <p>过滤：name LIKE %name% / subject_id LIKE 'subjectId%' / status='1'；
     * 排序：sort DESC；响应 misikt 风格 PageHelper 完整结构。
     *
     * <p>裸返回 MisiktPageVo —— advice 自动包 envelope，message 走 "成功"。
     */
    @SaCheckLogin
    @PostMapping("/page")
    public MisiktPageVo<PaperListItemVo> page(@RequestBody(required = false) PaperPageBo bo) {
        if (bo == null) {
            bo = new PaperPageBo();
        }
        return paperLibraryService.page(bo);
    }
}
