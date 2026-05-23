package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.CreateExamPaperBo;
import org.dromara.book.domain.bo.PaperLazyTreeBo;
import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.vo.CreateExamPaperVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.PaperCategoryNodeVo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.service.IPaperDetailService;
import org.dromara.book.service.IPaperLibraryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 卷库 Controller（D 卡 V0.5 卷库视觉级还原 + E 卡段② 试卷详情）。
 *
 * <p>路径前缀 {@code /teacher/exam/paper}，命中 {@link MisiktEnvelopeAdvice} 自动转 envelope
 * {@code {code:1, message:"成功", response:...}}。
 *
 * <p>⚠️ 关键：本 Controller 走裸返回（不走 R&lt;T&gt;）—— advice 裸返回路径 message 用 "成功"
 * 对齐 misikt 真响应字节级；R&lt;T&gt; 路径 message 用 "操作成功"（V1 兼容，回归测试断言依赖）。
 *
 * <p>3 个端点：
 * <ul>
 *   <li>POST /teacher/exam/paper/lazyTree — 试卷分类树（97 节点 / 3 根）</li>
 *   <li>POST /teacher/exam/paper/page — 试卷分页列表（misikt PageHelper 完整结构）</li>
 *   <li>POST /teacher/exam/paper/detail — 试卷详情（卷头 + sections 分组 + 题列表，E 卡段②）</li>
 * </ul>
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/exam/paper")
@RequiredArgsConstructor
public class PaperLibraryController {

    private final IPaperLibraryService paperLibraryService;
    private final IPaperDetailService paperDetailService;

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

    /**
     * POST /teacher/exam/paper/detail — 试卷详情（E 卡段②）。
     *
     * <p>请求 body：{@code {"paperId": 2798}}
     *
     * <p>响应：{@link PaperDetailVo}（卷头 + sections 分组 + 题列表，含 freeTags / questionKnowledges / 三图）。
     *
     * <p>边界：
     * <ul>
     *   <li>paperId 缺失 / null / 非数字 → 抛 NumberFormatException → 走全局异常处理</li>
     *   <li>paper 不存在 / status≠'1' → response: null</li>
     * </ul>
     *
     * <p>裸返回 PaperDetailVo —— advice 自动包 envelope，message 走 "成功"（misikt 风格）。
     */
    @SaCheckLogin
    @PostMapping("/detail")
    public PaperDetailVo detail(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("paperId") == null) {
            return null;
        }
        Long paperId = Long.valueOf(body.get("paperId").toString());
        return paperDetailService.getPaperDetail(paperId);
    }

    /**
     * POST /teacher/exam/paper/create — Q 卡段① 创建试卷。
     *
     * <p>请求 body：{@code {"name": "卷名", "questionIds": [1,2,3], "paperCategoryId": null}}
     *
     * <p>业务：写 biz_paper（status='1' 发布 / create_by=currentUserId / paper_type=1 手工）+
     * biz_paper_section（默认 "题目" 分组）+ 批量 biz_paper_question。@Transactional 整体回滚。
     *
     * <p>响应：{@link CreateExamPaperVo}（paperId + questionCount）— FE 拿 paperId 跳卷详情。
     *
     * <p>裸返回 —— advice 自动包 envelope，message 走 "成功"。
     */
    @SaCheckLogin
    @PostMapping("/create")
    public CreateExamPaperVo create(@Valid @RequestBody CreateExamPaperBo bo) {
        return paperLibraryService.createExamPaper(bo);
    }
}
