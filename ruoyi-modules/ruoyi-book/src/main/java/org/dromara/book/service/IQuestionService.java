package org.dromara.book.service;

import org.dromara.book.domain.bo.CreateQuestionBo;
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

import java.util.List;

/**
 * 题目 Service 接口。
 *
 * @author backend-dev
 */
public interface IQuestionService {

    /**
     * 分页查询题目（POST /teacher/question/page）。
     *
     * @param bo 分页 + 筛选入参（misikt 风格 pageIndex / keyWord / difficult / 等）
     * @return misikt 风格分页 VO
     */
    MisiktPageVo<QuestionItemVo> page(QuestionPageBo bo);

    /**
     * 单题详情查询（POST /teacher/question/select/{id}）。
     *
     * @param id 题目 ID
     * @return 详情 VO（含 questionKnowledges + questionStdKnowledges）；不存在返 null
     */
    QuestionDetailVo selectById(Long id);


    /**
     * Q' 卡段① — 批量按 id 拉题目详情（GET /teacher/question/list?ids=）。
     *
     * <p>用于试卷预览 PDF 导出场景。返回字段含
     * answer / explain / stemImg / options / freeTags / questionKnowledges，FE 渲染必须的全集。
     *
     * <p>实现要点：
     * <ul>
     *   <li>软删过滤 status&lt;&gt;'2'</li>
     *   <li>按入参 ids 顺序重排（LinkedHashMap by id，不依赖 SQL FIND_IN_SET）</li>
     *   <li>复用 page/select 现有 loadKnowledgesByQuestionIds + loadFreeTagsByQuestionIds</li>
     * </ul>
     *
     * @param ids 题目 ID 列表（非空，上限 Controller 兜底 100）
     * @return 详情 VO 列表（按入参顺序，软删题被剔除导致长度可能小于入参）
     */
    List<QuestionDetailVo> listByIds(List<Long> ids);

    /**
     * PRD-A-007 T1 — 换一题（POST /teacher/question/replace）。
     *
     * <p>候选逻辑（两步降级）：
     * <ol>
     *   <li>优先：同 subject_id + 同首考点（biz_question_knowledge source='U' 首条 knowledge_id）
     *       + 同 question_type + id NOT IN excludeIds + status='1'（非软删）</li>
     *   <li>兜底：同 subject_id + 同 question_type + id NOT IN excludeIds + status='1'</li>
     *   <li>仍无 → 返 null（FE 提示"暂无可替换同类题"，不报错）</li>
     * </ol>
     *
     * <p>🔴 biz_question 无 tenant_id 列，实现必须走
     * {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹（同 PRD-A-005 G4 坑）。
     *
     * @param bo 换题入参（currentQuestionId + excludeIds）
     * @return 候选 QuestionDetailVo（与 listByIds 单元素同构）；无候选返 null
     */
    QuestionDetailVo replaceQuestion(ReplaceQuestionBo bo);

    /**
     * 组卷草稿（POST /teacher/question/genExamData/）。
     *
     * <p>取当前用户试题筐内全部已发布题（status='1'），按 questionType 分组生成 sections —
     * 1=选择/4=填空/5=简答，按 misikt 真实行为固定顺序 1→4→5；不出现的题型不返。
     *
     * <p>草稿不落库，跟 misikt 一致（FE 工作台本地 state 操作）。
     *
     * @param userId 当前用户 ID（不可空）
     * @return 草稿 VO（sections 按 questionType 顺序，空筐返 sections=[]）
     */
    ExamDataVo genExamData(Long userId);

    /**
     * PRD-C-007 T1 — 题目 5 维度打标回写（POST /teacher/question/update-label）。
     *
     * <p>仅 UPDATE 打标列（dim1_kp_id / dim2_qtype / dim4_difficulty / dim5_structure /
     * label_status / label_confidence / labeled_by / labeled_at），不碰题干/答案。
     * labeled_at 服务端取 now。
     * （🔴 V905 schema 收敛：dim3_skill / aux_tags 列已 DROP，不再回写。）
     *
     * <p>🔴 必须 {@code DataPermissionHelper.ignore} 包裹 —— biz_question 老题 create_user=2 ≠ 登录 id，
     * 数据权限拦截器会注入 {@code AND create_by=登录id} → 0 行静默假成功（PRD-A-002 实战坑）。
     *
     * @param bo 打标入参（questionId 必填，dim 与 label 维度值）
     */
    void updateLabel(UpdateLabelBo bo);

    /**
     * PRD-A-015 属性编辑页回写（POST /teacher/question/update-attrs）：基础属性 + 5维打标 + N1 高级列。
     *
     * <p>全字段可选——只回写传了的（非 null）列，不传保持原值。权限：本人题 or superadmin。
     * 不碰 blockJson/题干（那走 {@link #update}）。
     *
     * @param bo 属性入参（questionId 必填，其余可选）
     * @return 回读的最新详情 VO
     */
    QuestionDetailVo updateAttrs(UpdateAttrsBo bo);

    /**
     * PRD-C-009 — teacher 侧录题（POST /teacher/question/create）。
     *
     * <p>归属 = 登录老师（绝不信前端 createBy）。落库一个事务原子完成：
     * <ol>
     *   <li>INSERT biz_question（create_user = 登录 id，create_by/update_by = String.valueOf(登录 id)，
     *       status='1' 已发布，直列 difficult/subjectId/*Img/exam_* + AI 血缘
     *       motherQuestionId/variantRelation/importSource + 打标列 dim1/2/4/5）</li>
     *   <li>对 stem(必)/answer/analyze(各非空才写) 逐个 INSERT biz_text_content
     *       (content_type='S'/'A'/'E')，拿到各行 id</li>
     *   <li>updateById 回写 stem/answer/analyze TextContentId FK 到 biz_question</li>
     * </ol>
     *
     * <p>🔴 biz_question / biz_text_content 均无 tenant_id 列，且老题 create_user≠登录 id，
     * 全事务体走 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹
     * （否则 BaseMapper 继承 insert/updateById 被多租户拦截器注入 tenant_id 报 Unknown column
     * 整事务回滚；数据权限拦截器注入 AND create_by=登录id 致 FK 回写 0 行静默假成功）。
     *
     * <p>🔴 stem_text 老字段不写（题面事实源走外置；读端 selectQuestionDetailById 已
     * COALESCE(tc_s.content, q.stem_text)，answer/explain 唯一来源 = tc_a/tc_e）。
     *
     * @param bo 录题入参（questionType + stem 必填）
     * @return 新建题目的详情 VO（读回外置文本 + knowledges + freeTags）
     */
    QuestionDetailVo create(CreateQuestionBo bo);

    /**
     * PRD-C-015 批4·缺口10 —— teacher 侧「覆盖原行」更新（POST /teacher/question/update）。
     *
     * <p>举一反三「重生后再入库 = 覆盖原行」：按 {@code bo.id} UPDATE biz_question 直列 +
     * 重写题面三要素（biz_text_content S/A/E）+ 重写子表（knowledge / free_tag / ai，先清后写，幂等）。
     *
     * <p>归属校验：只许改自己的题（create_user = 登录老师才放行，否则抛 ServiceException）；
     * create_user 不动、update_by/update_time 刷新；status 不动（仍 '1'）。
     *
     * <p>🔴 全事务体走 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))}（同 create，
     * biz_question / biz_text_content 无 tenant_id + 老题 create_user≠登录 id）。
     *
     * @param bo 覆盖更新入参（id + questionType + stem 必填）
     * @return 更新后的题目详情 VO
     */
    QuestionDetailVo update(UpdateQuestionBo bo);

    /**
     * PRD-C-014 B1 T4 —— 某知识点（kpId）下高频标签候选池
     * （GET /teacher/question/tagsByKp?kpId=&limit=）。
     *
     * <p>SQL = biz_question_free_tag ⨝ biz_question_knowledge（同 question_id 且 knowledge_id=kpId）
     * ⨝ biz_free_tag，按 tag 聚合 count desc limit N。供 W1 标签候选池替代旧单建接口。
     *
     * @param kpId  知识点 ID（biz_subject.id；空 / 空白返空 list）
     * @param limit 返回上限（兜底 300，clamp 1~1000）
     * @return 标签候选列表（{id,name,count}，count 倒序）；无命中返空 list
     */
    List<KpTagStatVo> tagsByKp(String kpId, Integer limit);

    /**
     * PRD-A-015 — 题目结构化网格块编辑（POST /teacher/question/update-block）。
     *
     * <p>🔴 C-100 B-converge 改名：原 A-015 = {@code update(UpdateQuestionBo)} + {@code /teacher/question/update}，
     * 与 C-015「覆盖原行」撞名撞端点，维护者拍板 A 整体改名 → {@code updateBlock(UpdateBlockBo)} + {@code /update-block}。
     *
     * <p>权威源 = blockJson（§10.1 schema）。落库一个事务原子完成：
     * <ol>
     *   <li>OWNER 校验：原题 create_user ≠ 登录 id → 抛 ServiceException（他人题 + 公共题都被挡，G5）</li>
     *   <li>校验 blockJson（{@link org.dromara.book.util.BlockJsonValidator}），非法抛异常</li>
     *   <li>upsert biz_question_block（question_id 为 PK，先 selectById 判存在 → update/insert，
     *       v=1，update_by = 登录 id）</li>
     *   <li>可选元数据：传了 questionType/difficult/subjectId 更新 biz_question 对应列；
     *       传了 stem/answer/analyze（非空）同步更新对应 biz_text_content（无则新建并回写 FK）</li>
     * </ol>
     *
     * <p>🔴 biz_question / biz_text_content / biz_question_block 均无 tenant_id 列；且老题
     * create_user≠登录 id，全事务体走 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))}
     * 包裹（同 create() 范式：否则数据权限拦截器注入 AND create_by=登录id 致 OWNER 加载到 null / 更新 0 行）。
     *
     * @param bo 编辑入参（questionId + blockJson 必填）
     * @return 更新后题目的详情 VO（含 blockJson + 外置文本 + knowledges + freeTags）
     */
    QuestionDetailVo updateBlock(UpdateBlockBo bo);

    /**
     * PRD-C-100 BC3 — 清掉某题的结构化排版（删 biz_question_block 行）。
     *
     * <p>用途：举一反三里老师对已入库变式「手动排版」存过 blockJson 后又「重生」该题——重生产物题面
     * 已变，旧 blockJson 布局会与新题面对不上（详情/卷库「有 block 用 block」会渲染旧布局）。确认重生
     * 前调本方法删掉脏 block，让详情/卷库回落到按新题面纯文本渲染。
     *
     * <p>OWNER 校验同 {@link #updateBlock}（本人题 or superadmin）。block 行不存在 → 幂等返回（不报错）。
     * 不碰 biz_question 题面/答案/解析（那些由重生 update 覆盖）。
     *
     * @param questionId 题目 id（必填）
     */
    void deleteBlock(Long questionId);

    /**
     * PRD-A-021 R1a 题库归属状态机 —— 题草稿→正式（status 0→1）。
     *
     * <p>「公开」唯一动作：把本人草稿题（status='0'）提升为已发布（status='1'），从此进入公共/全站列表
     * + 卷库选题候选。仅本人题（create_user=登录老师）或 superadmin 可 promote，否则抛 ServiceException
     * 「无权公开非本人题目」。
     *
     * <p>幂等：题已是 '1' → 直接放行无副作用（不重复 UPDATE/不报错）。题不存在 / 已软删（status='2'）→
     * 抛 ServiceException。
     *
     * <p>🔴 biz_question 无 tenant_id 列 + 老题 create_user≠登录 id，全程走
     * {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹（同 deleteBlock 范式）。
     *
     * @param id 题目 id（必填）
     */
    void promote(Long id);

    /**
     * PRD-A-022 批0 —— 批量软删草稿（POST /teacher/question/discard-drafts）。
     *
     * <p>举一反三「换一批」时批量软删旧草稿。一条 UPDATE：
     * {@code biz_question SET status='2' WHERE id IN(ids) AND create_user=登录 id AND status='0'}
     * —— owner + 仅草稿 双约束，绝不碰他人题、绝不碰已发布 '1'。
     *
     * <p>ids 空 / null → 直接返回 0，不报错。未登录抛 {@link ServiceException}。
     *
     * <p>🔴 biz_question 无 tenant_id 列 + 老题 create_user≠登录 id，全程走
     * {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹（同 promote 范式）。
     *
     * @param ids 待软删的草稿题 id 列表
     * @return 受影响行数（实际软删的草稿数）
     */
    int discardDrafts(List<Long> ids);
}
