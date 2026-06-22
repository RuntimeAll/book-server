package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.CreateQuestionBo;
import org.dromara.book.domain.bo.DiscardDraftsBo;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.bo.ReplaceQuestionBo;
import org.dromara.book.domain.bo.UpdateAttrsBo;
import org.dromara.book.domain.bo.UpdateBlockBo;
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
import org.springframework.web.bind.annotation.PutMapping;
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
     * POST /teacher/question/update-block — PRD-A-015 题目结构化编辑。
     *
     * <p>🔴 C-100 B-converge 改名：原 A-015 端点 = {@code /teacher/question/update}，与 C-015
     * 「覆盖原行」撞名，维护者拍板 A 整体改名 → {@code /teacher/question/update-block} + {@link UpdateBlockBo}。
     *
     * <p>权威源 = blockJson（§10.1 结构化网格块 schema）。挂在 {@code /teacher/**}，
     * 自动命中 {@link MisiktEnvelopeAdvice} 包 envelope（200→code 1，异常透传非 code:1）。
     *
     * <p>入参（{@link UpdateBlockBo}）：questionId + blockJson 必填；
     * questionType/difficult/subjectId/stem/answer/analyze 可选元数据（传了才同步）。
     * 🔴 createUser/createBy/status/id 服务端强制，body 传了也忽略；编辑前做 OWNER 校验（非本人题拒）。
     *
     * <p>响应（envelope 拆后）= 更新后题目的 {@link QuestionDetailVo}（含 blockJson + 外置题面）。
     */
    @SaCheckLogin
    @PostMapping("/update-block")
    public R<QuestionDetailVo> updateBlock(@Validated @RequestBody UpdateBlockBo bo) {
        return R.ok(questionService.updateBlock(bo));
    }

    /**
     * POST /teacher/question/delete-block?questionId={id} — PRD-C-100 BC3 清结构化排版。
     *
     * <p>删 biz_question_block 行，让详情/卷库回落纯文本渲染。用途：举一反三里老师对已入库变式
     * 「手动排版」存过 blockJson 后又「重生」该题——重生覆盖题面后旧布局对不上，确认重生前清掉脏 block。
     *
     * <p>OWNER 校验（本人题 or superadmin）；block 不存在幂等返回。挂 {@code /teacher/**} 命中 envelope。
     *
     * @param questionId 题目 id
     */
    @SaCheckLogin
    @PostMapping("/delete-block")
    public R<Void> deleteBlock(@RequestParam("questionId") Long questionId) {
        questionService.deleteBlock(questionId);
        return R.ok();
    }

    /**
     * PUT /teacher/question/promote/{id} — PRD-A-021 R1a 题库归属状态机·公开（草稿→正式）。
     *
     * <p>把本人草稿题（biz_question.status='0'）提升为已发布（'1'），从此进入公共/全站列表 + 卷库选题候选。
     * 「公开」唯一动作。OWNER 校验（本人题 or superadmin，否则「无权公开非本人题目」）；已 '1' 幂等放行；
     * 软删 '2' / 不存在 → 异常。
     *
     * <p>挂 {@code /teacher/**} 命中 {@link MisiktEnvelopeAdvice} 包 envelope（200→code 1，异常透传非 code:1）。
     *
     * @param id 题目 id（路径参数）
     */
    @SaCheckLogin
    @PutMapping("/promote/{id}")
    public R<Void> promote(@PathVariable("id") Long id) {
        questionService.promote(id);
        return R.ok();
    }

    /**
     * POST /teacher/question/discard-drafts — PRD-A-022 批0 批量软删草稿。
     *
     * <p>举一反三「换一批」时批量软删旧草稿。一条 UPDATE 把本人草稿题（status='0'）软删为 '2'：
     * {@code WHERE id IN(ids) AND create_user=登录 id AND status='0'} —— owner + 仅草稿双约束，
     * 绝不碰他人题、绝不碰已发布 '1'。ids 空 / null → 返回 0，不报错。
     *
     * <p>挂在 {@code /teacher/**}，自动命中 {@link MisiktEnvelopeAdvice} 包 envelope（200→code 1）。
     *
     * @param bo 入参（ids = 待软删草稿题 id 列表）
     * @return 受影响行数（实际软删的草稿数）
     */
    @SaCheckLogin
    @PostMapping("/discard-drafts")
    public R<Integer> discardDrafts(@RequestBody DiscardDraftsBo bo) {
        return R.ok(questionService.discardDrafts(bo.getIds()));
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

    /**
     * POST /teacher/question/update-attrs — PRD-A-015 属性编辑页回写（基础属性 + 5维打标 + N1 高级列）。
     *
     * <p>与 {@code /update}（C-015 覆盖原行）、{@code /update-block}（A-015 排版=blockJson）、
     * {@code /update-label}（AI LangGraph 打标）分工：本端点是「老师属性编辑页」专用，
     * 全字段可选（只回写传了的列），不碰 blockJson/题干。
     * 🔴 C-100 方案B：dim3_skill / aux_tags 两维已随 V905 DROP 剥除（属性编辑页 C 线预期降级）。
     * 权限：本人题 or superadmin（service 内 owner||isSuperAdmin 校验）。
     */
    @SaCheckLogin
    @PostMapping("/update-attrs")
    public R<QuestionDetailVo> updateAttrs(@Validated @RequestBody UpdateAttrsBo bo) {
        return R.ok(questionService.updateAttrs(bo));
    }
}
