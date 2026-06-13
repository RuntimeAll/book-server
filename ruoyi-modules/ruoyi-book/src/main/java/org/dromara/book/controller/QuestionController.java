package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.CreateQuestionBo;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.bo.ReplaceQuestionBo;
import org.dromara.book.domain.bo.UpdateLabelBo;
import org.dromara.book.domain.bo.UpdateQuestionBo;
import org.dromara.book.domain.vo.ExamDataVo;
import org.dromara.book.domain.vo.KpTagStatVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.service.IQuestionService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 题目 Controller（/teacher/question/page + /teacher/question/select/{id}）。
 *
 * <p>路径前缀 {@code /teacher/question}，命中 {@link MisiktEnvelopeAdvice} 自动转 envelope。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/question")
@RequiredArgsConstructor
public class QuestionController {

    private final IQuestionService questionService;

    /**
     * POST /teacher/question/page — 分页拉题。
     *
     * <p>入参 BO 字段命名严格按 misikt（pageIndex 不是 pageNum）；响应 PageHelper 完整结构。
     */
    @SaCheckLogin
    @PostMapping("/page")
    public R<MisiktPageVo<QuestionItemVo>> page(@RequestBody(required = false) QuestionPageBo bo) {
        if (bo == null) {
            bo = new QuestionPageBo();
        }
        return R.ok(questionService.page(bo));
    }

    /**
     * POST /teacher/question/select/{id} — 单题详情。
     *
     * <p>响应含 questionKnowledges（U 轨）+ questionStdKnowledges（S 轨）。
     */
    @SaCheckLogin
    @PostMapping("/select/{id}")
    public R<QuestionDetailVo> select(@PathVariable("id") Long id) {
        return R.ok(questionService.selectById(id));
    }


    /**
     * POST /teacher/question/genExamData/ — 组卷草稿。
     *
     * <p>无入参（path 末尾 trailing slash 是 misikt 真实特征 — FE TS 契约同样带斜杠）；
     * 拿当前用户筐内全部已发布题，按 questionType 1→4→5 分组返 sections，
     * 草稿不落库（FE 工作台本地 state）。
     */
    @SaCheckLogin
    @PostMapping("/genExamData/")
    public R<ExamDataVo> genExamData() {
        Long userId = LoginHelper.getUserId();
        return R.ok(questionService.genExamData(userId));
    }

    /**
     * POST /teacher/question/replace — PRD-A-007 T1 换一题。
     *
     * <p>入参：{@code {"currentQuestionId":Long, "excludeIds":Long[]}}（excludeIds = 本卷已有全部题 id）。
     * 响应（envelope 拆后）：一道 {@link QuestionDetailVo}（与 listByIds 单元素同构）或 null。
     * FE 收到 null 时提示"暂无可替换的同类题"，不报错。
     *
     * <p>挂在 {@code /teacher/**}，自动命中 {@link MisiktEnvelopeAdvice} 包 envelope。
     */
    @SaCheckLogin
    @PostMapping("/replace")
    public R<QuestionDetailVo> replace(@RequestBody @Valid ReplaceQuestionBo bo) {
        return R.ok(questionService.replaceQuestion(bo));
    }

    /**
     * GET /teacher/question/list?ids=1,2,3 — Q' 卡段① 批量按 id 拉详情。
     *
     * <p>试卷预览 PDF 导出场景：basket cache 字段不全，进模态时调本端点拉一次。
     * 返回字段含 answer / explain / freeTags / questionKnowledges / stemImg 等 FE 渲染必须的全集。
     * 入参上限 100（防超大请求），软删题（status='2'）自动过滤，
     * 返回顺序按入参 ids 保序（Service 端 LinkedHashMap 重排）。
     */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<QuestionDetailVo>> listByIds(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        if (ids.size() > 100) {
            throw new ServiceException("单次最多 100 题导出");
        }
        return R.ok(questionService.listByIds(ids));
    }

    /**
     * POST /teacher/question/update-label — PRD-C-007 T1 题目 5 维度打标回写。
     *
     * <p>由 teacher-copilot LangGraph persist 节点调用（双头鉴权 + camelCase body）。
     * 挂在 {@code /teacher/**}，自动命中 {@link MisiktEnvelopeAdvice} 包成 {@code {code:1,message,response}}。
     *
     * <p>入参（{@link UpdateLabelBo}）：questionId(必填) / dim1KpId / dim2Qtype /
     * dim4Difficulty / dim5Structure / labelStatus(1或2,必填) / labelConfidence(0-1) / labeledBy。
     * （🔴 V905 schema 收敛：dim3Skill / auxTags 入参已移除，对应列已 DROP。）
     * labeled_at 服务端取 now。仅 UPDATE 打标列，不碰题干/答案。
     *
     * <p>🔴 service 走 {@code DataPermissionHelper.ignore} —— 否则 biz_question 老题
     * create_user=2 ≠ 登录 id 被数据权限拦截器拦成 0 行静默假成功（PRD-A-002 坑）。
     */
    @SaCheckLogin
    @PostMapping("/update-label")
    public R<Void> updateLabel(@Validated @RequestBody UpdateLabelBo bo) {
        questionService.updateLabel(bo);
        return R.ok("操作成功");
    }

    /**
     * POST /teacher/question/create — PRD-C-009 teacher 侧录题。
     *
     * <p>由教师端工作台 / AI-Orchestrator（双头鉴权 ruoyi_username 登录后，满足 @SaCheckLogin）调用。
     * 挂在 {@code /teacher/**}，自动命中 {@link MisiktEnvelopeAdvice} 包 envelope（200→code 1）。
     *
     * <p>入参（{@link CreateQuestionBo}，camelCase body）：questionType + stem 必填；
     * answer/analyze 长文本 + difficult/subjectId/*Img/exam_* 直列 +
     * AI 血缘 motherQuestionId/variantRelation/importSource + 5 维度打标列 +
     * （PRD-C-014 B1）副 kp secondaryKpIds / 标签 tags / DNA skeleton/scene/examType/hardPoints/
     * 锚定 anchorId/needAnchorReview/reasoning（均可选，事务内拆写 knowledge / free_tag×2 / ai 表）。
     * 🔴 createBy/createUser/status/id 服务端强制，body 传了也忽略（归属 = 登录老师）。
     *
     * <p>响应（envelope 拆后）= 新建题目的 {@link QuestionDetailVo}（读回外置题面 + knowledges + freeTags）。
     */
    @SaCheckLogin
    @PostMapping("/create")
    public R<QuestionDetailVo> create(@Validated @RequestBody CreateQuestionBo bo) {
        return R.ok(questionService.create(bo));
    }

    /**
     * POST /teacher/question/update — PRD-C-015 批4·缺口10 覆盖原行。
     *
     * <p>举一反三「重生后再入库 = 覆盖原行」：AI 改了 DNA / 重生题面后，把**已入库**的题
     * 按 {@code id} UPDATE（而非新写一行）。重写题面三要素 + knowledge / free_tag / ai
     * 子表（先清后写，幂等）。归属由后端校验（只许改自己的题），create_user 不动、update_by/time 刷新。
     *
     * <p>入参 {@link UpdateQuestionBo}（= CreateQuestionBo + 必填 id）。挂 {@code /teacher/**}
     * 走 {@link MisiktEnvelopeAdvice} 包 envelope（200→code 1）。
     *
     * @param bo 覆盖更新入参（id + questionType + stem 必填）
     * @return 更新后的题目详情 VO
     */
    @SaCheckLogin
    @PostMapping("/update")
    public R<QuestionDetailVo> update(@Validated @RequestBody UpdateQuestionBo bo) {
        return R.ok(questionService.update(bo));
    }

    /**
     * GET /teacher/question/tagsByKp?kpId={id}&limit=300 — PRD-C-014 B1 T4
     * 某知识点下高频标签候选池。
     *
     * <p>SQL = biz_question_free_tag ⨝ biz_question_knowledge（同 question_id 且 knowledge_id=kpId）
     * ⨝ biz_free_tag，按 tag 聚合 count desc limit N。供 W1 标签候选池（替代旧 H6 单建接口）。
     *
     * <p>挂在 {@code /teacher/**}，命中 {@link MisiktEnvelopeAdvice} 包 envelope（200→code 1）。
     * 老师登录态（@SaCheckLogin）。kpId 空 / 无命中返空数组。
     *
     * @param kpId  知识点 ID（biz_subject.id）
     * @param limit 返回上限（默认 300，service clamp 1~1000）
     * @return 标签候选 {@code [{id,name,count}]}（count 倒序）
     */
    @SaCheckLogin
    @GetMapping("/tagsByKp")
    public R<List<KpTagStatVo>> tagsByKp(@RequestParam("kpId") String kpId,
                                         @RequestParam(value = "limit", required = false, defaultValue = "300") Integer limit) {
        return R.ok(questionService.tagsByKp(kpId, limit));
    }
}
