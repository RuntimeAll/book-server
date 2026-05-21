package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.QuestionPaperVo;
import org.dromara.book.service.IQuestionDetailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目详情衍生 Controller（PRD §3.1 B-6/B-7/B-8/B-9 共 4 端点）。
 *
 * <p>本 controller 聚合 4 个详情页衍生端点 — 路径前缀分散在
 * {@code /teacher/qd}、{@code /teacher/center}、{@code /teacher/question} 三处，
 * 所以 class 不加 {@code @RequestMapping} 前缀，每个方法用完整路径。
 *
 * <p>4 端点：
 * <ul>
 *   <li>GET /teacher/qd/papers/{id}        — 这题在哪些卷（真实 JOIN 查询，B-6）</li>
 *   <li>GET /teacher/qd/stats/{id}         — 题目统计 mock（B-7）</li>
 *   <li>GET /teacher/center/q-folder/tree  — 收藏夹分类树 mock 单条（B-8）</li>
 *   <li>GET /teacher/question/similar/{id} — 相似题 mock 空数组（B-9）</li>
 * </ul>
 *
 * <p>统一 GET / 鉴权 {@code @SaCheckLogin} / 路径前缀 {@code /teacher/*} 命中
 * {@link MisiktEnvelopeAdvice} 自动包 envelope — Controller 直接返业务对象 / list / map。
 *
 * @author backend-dev
 */
@RestController
@RequiredArgsConstructor
public class QuestionDetailController {

    private final IQuestionDetailService questionDetailService;

    /**
     * GET /teacher/qd/papers/{id} — 该题出现在哪些卷（B-6 真实查询）。
     *
     * @param id 题目 id
     * @return {@code [{examPaperId, examPaperName, examYear?, sort?}]}（可能为空 list）
     */
    @SaCheckLogin
    @GetMapping("/teacher/qd/papers/{id}")
    public List<QuestionPaperVo> papers(@PathVariable("id") Long id) {
        return questionDetailService.papers(id);
    }

    /**
     * GET /teacher/qd/stats/{id} — 题目统计 mock（B-7）。
     *
     * <p>真实统计依赖 M6 班级/作业回填，本卡返 mock 全 0；FE UI 优雅显示。
     *
     * @param id 题目 id（mock 不查 DB，入参仅占位）
     * @return {@code {attempts, correctCnt, accuracy, paperCount, reportCount, answerDist}}
     */
    @SaCheckLogin
    @GetMapping("/teacher/qd/stats/{id}")
    public Map<String, Object> stats(@PathVariable("id") Long id) {
        // 用 LinkedHashMap 保留契约字段顺序（FE 调试时 JSON 可读性更高）
        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("attempts", 0);
        result.put("correctCnt", 0);
        result.put("accuracy", 0.0);
        result.put("paperCount", 0);
        result.put("reportCount", 0);
        result.put("answerDist", Collections.emptyList());
        return result;
    }

    /**
     * GET /teacher/center/q-folder/tree — 收藏夹分类树 mock 单条"我的试题"（B-8）。
     *
     * <p>本卡接受 mock 单条；后续收藏夹 CRUD 实装时改走 service 查 biz_question_folder。
     *
     * @return {@code [{id, name, pid, count, children:[]}]} — 单条 mock
     */
    @SaCheckLogin
    @GetMapping("/teacher/center/q-folder/tree")
    public List<Map<String, Object>> folderTree() {
        Map<String, Object> root = new LinkedHashMap<>(5);
        root.put("id", 0L);
        root.put("name", "我的试题");
        root.put("pid", 0L);
        root.put("count", 0);
        root.put("children", Collections.emptyList());
        return Collections.singletonList(root);
    }

    /**
     * GET /teacher/question/similar/{id} — 相似题 mock 空数组（B-9）。
     *
     * <p>真实算法需 simhash + knowledge 相似度等 v2 算法接入；本卡返空数组。
     *
     * @param id 题目 id（mock 不查 DB，入参仅占位）
     * @return {@code []} — 空 list
     */
    @SaCheckLogin
    @GetMapping("/teacher/question/similar/{id}")
    public List<Object> similar(@PathVariable("id") Long id) {
        return Collections.emptyList();
    }
}
