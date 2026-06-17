package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.book.domain.bo.CreateQuestionBo;
import org.dromara.book.domain.bo.QuestionPageBo;
import org.dromara.book.domain.bo.ReplaceQuestionBo;
import org.dromara.book.domain.bo.UpdateLabelBo;
import org.dromara.book.domain.bo.UpdateQuestionBo;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizQuestionAi;
import org.dromara.book.domain.entity.BizQuestionKnowledge;
import org.dromara.book.domain.entity.BizTextContent;
import org.dromara.book.domain.vo.ExamDataVo;
import org.dromara.book.domain.vo.ExamSectionVo;
import org.dromara.book.domain.vo.FreeTagVo;
import org.dromara.book.domain.vo.KpTagStatVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.book.mapper.BizFreeTagWriteMapper;
import org.dromara.book.mapper.BizQuestionAiMapper;
import org.dromara.book.mapper.BizQuestionFreeTagMapper;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizTextContentMapper;
import org.dromara.book.service.IQuestionBasketService;
import org.dromara.book.service.IQuestionService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目 Service 实现。
 *
 * <p>page / select 两端点公用 knowledges 回填策略：
 * <ol>
 *   <li>主表 SQL 查 list（page）或单条（select），不带 knowledges 关联</li>
 *   <li>二次查 biz_question_knowledge LEFT JOIN biz_subject 按 questionId 集合批量拿</li>
 *   <li>按 source='U' / 'S' 分组回填到对应字段</li>
 * </ol>
 *
 * <p>避免单条 list × N 次查（N+1 问题），且 mapper.xml 主表 SQL 结构清晰。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements IQuestionService {

    private final BizQuestionMapper bizQuestionMapper;
    private final BizQuestionKnowledgeMapper bizQuestionKnowledgeMapper;
    private final BizQuestionFreeTagMapper bizQuestionFreeTagMapper;
    private final BizFreeTagWriteMapper bizFreeTagWriteMapper;
    private final BizQuestionAiMapper bizQuestionAiMapper;
    private final BizTextContentMapper bizTextContentMapper;
    private final IQuestionBasketService questionBasketService;

    /** 副 kp 上限（≤3，越界丢弃，见字段映射文档 secondary_kps） */
    private static final int MAX_SECONDARY_KP = 3;
    /** 标注 schema 版本默认值（主表 + ai 表一致） */
    private static final int DEFAULT_ANNOTATE_VERSION = 1;
    /** T4 标签候选池默认 limit / clamp 上下限 */
    private static final int DEFAULT_TAGS_LIMIT = 300;
    private static final int MAX_TAGS_LIMIT = 1000;

    /** 录题默认导入来源（importSource 未传时填） */
    private static final String DEFAULT_IMPORT_SOURCE = "AI-Orchestrator";
    /** 题目格式版本默认码 */
    private static final int DEFAULT_QUESTION_VERSION = 1010;

    /** misikt 默认每页 10，pageIndex 兜底 1 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_INDEX = 1;

    @Override
    public MisiktPageVo<QuestionItemVo> page(QuestionPageBo bo) {
        int pageIndex = bo.getPageIndex() == null || bo.getPageIndex() <= 0
            ? DEFAULT_PAGE_INDEX : bo.getPageIndex();
        int pageSize = bo.getPageSize() == null || bo.getPageSize() <= 0
            ? DEFAULT_PAGE_SIZE : bo.getPageSize();

        QueryWrapper<BizQuestion> wrapper = buildPageWrapper(bo);

        // J 卡段②：注入当前 userId → mapper LEFT JOIN biz_question_favorite 直接出 isFavorite，
        // 免 FE N+1 调用 /qd/favorite/{id}。@SaCheckLogin 在 Controller 兜底，此处不为 null。
        Long currentUserId = LoginHelper.getUserId();

        // PRD-C-009「我的题库」：mine=true → 只看当前登录老师自己的题（create_user=自己）。
        // owner 由后端定（绝不信前端传 userId），前端只传 mine 开关。
        if (Boolean.TRUE.equals(bo.getMine())) {
            wrapper.eq("q.create_user", currentUserId);
        }

        Page<QuestionItemVo> mpPage = new Page<>(pageIndex, pageSize);
        IPage<QuestionItemVo> result = bizQuestionMapper.selectQuestionPage(mpPage, wrapper, currentUserId);

        // 回填 questionKnowledges（source='U'） + freeTags（X 卡段②）
        List<QuestionItemVo> records = result.getRecords();
        if (records != null && !records.isEmpty()) {
            List<Long> ids = records.stream().map(QuestionItemVo::getId).collect(Collectors.toList());
            Map<Long, List<QuestionKnowledgeVo>> uMap = loadKnowledgesByQuestionIds(ids, "U");
            Map<Long, List<FreeTagVo>> ftMap = loadFreeTagsByQuestionIds(ids);
            for (QuestionItemVo vo : records) {
                vo.setQuestionKnowledges(uMap.getOrDefault(vo.getId(), Collections.emptyList()));
                vo.setFreeTags(ftMap.getOrDefault(vo.getId(), Collections.emptyList()));
            }
        }

        return MisiktPageVo.of(result);
    }

    @Override
    public QuestionDetailVo selectById(Long id) {
        if (id == null) {
            return null;
        }
        QuestionDetailVo vo = bizQuestionMapper.selectQuestionDetailById(id);
        if (vo == null) {
            return null;
        }
        // 回填 questionKnowledges + questionStdKnowledges + freeTags（X 卡段②）
        List<Long> ids = Collections.singletonList(id);
        vo.setQuestionKnowledges(loadKnowledgesByQuestionIds(ids, "U").getOrDefault(id, new ArrayList<>()));
        vo.setQuestionStdKnowledges(loadKnowledgesByQuestionIds(ids, "S").getOrDefault(id, new ArrayList<>()));
        vo.setFreeTags(loadFreeTagsByQuestionIds(ids).getOrDefault(id, new ArrayList<>()));
        return vo;
    }

    /**
     * Q' 卡段① — 批量按 id 拉题目详情（试卷预览 PDF 导出场景）。
     *
     * <p>实现：
     * <ol>
     *   <li>mapper 批查（IN + status&lt;&gt;'2'）拿到无序 list</li>
     *   <li>复用 {@link #loadKnowledgesByQuestionIds} 拿 U/S knowledges + {@link #loadFreeTagsByQuestionIds} 拿 freeTags</li>
     *   <li>回填到每条 vo</li>
     *   <li>用 LinkedHashMap by id 按入参 ids 顺序重排（不依赖 SQL FIND_IN_SET）</li>
     * </ol>
     */
    @Override
    public List<QuestionDetailVo> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<QuestionDetailVo> raw = bizQuestionMapper.selectQuestionDetailByIds(ids);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        // 二次批量拉 knowledges + freeTags
        Map<Long, List<QuestionKnowledgeVo>> uMap = loadKnowledgesByQuestionIds(ids, "U");
        Map<Long, List<QuestionKnowledgeVo>> sMap = loadKnowledgesByQuestionIds(ids, "S");
        Map<Long, List<FreeTagVo>> ftMap = loadFreeTagsByQuestionIds(ids);

        // 按 id 索引（保序的中间结构 — DB 返回顺序不保证）
        Map<Long, QuestionDetailVo> byId = new LinkedHashMap<>(raw.size() * 2);
        for (QuestionDetailVo vo : raw) {
            vo.setQuestionKnowledges(uMap.getOrDefault(vo.getId(), new ArrayList<>()));
            vo.setQuestionStdKnowledges(sMap.getOrDefault(vo.getId(), new ArrayList<>()));
            vo.setFreeTags(ftMap.getOrDefault(vo.getId(), new ArrayList<>()));
            byId.put(vo.getId(), vo);
        }

        // 按入参 ids 顺序重排（软删 / 不存在的 id 自动跳过）
        List<QuestionDetailVo> ordered = new ArrayList<>(byId.size());
        for (Long id : ids) {
            QuestionDetailVo vo = byId.get(id);
            if (vo != null) {
                ordered.add(vo);
            }
        }
        return ordered;
    }


    /**
     * PRD-A-007 T1 — 换一题实现。
     *
     * <p>候选策略（两步降级）：
     * <ol>
     *   <li>优先：同 subject_id + 同首考点（biz_question_knowledge source='U' 首条 knowledge_id）
     *       + 同 question_type + id NOT IN excludeIds + status='1'，LIMIT 1</li>
     *   <li>兜底：同 subject_id + 同 question_type + id NOT IN excludeIds + status='1'，LIMIT 1</li>
     *   <li>仍无 → 返 null</li>
     * </ol>
     *
     * <p>🔴 biz_question 无 tenant_id 列，全程走 TenantHelper.ignore + DataPermissionHelper.ignore
     * 线程级包裹（同 PRD-A-005 G4 坑，BaseMapper 继承方法 namespace 落 BaseMapper 类级
     * @InterceptorIgnore 命中不到，必须线程级）。
     */
    @Override
    public QuestionDetailVo replaceQuestion(ReplaceQuestionBo bo) {
        if (bo == null || bo.getCurrentQuestionId() == null) {
            return null;
        }

        return TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            Long currentId = bo.getCurrentQuestionId();

            // 构造排除集合：excludeIds 合并 currentQuestionId 自身
            List<Long> excludeIds = bo.getExcludeIds() == null
                ? new ArrayList<>() : new ArrayList<>(bo.getExcludeIds());
            if (!excludeIds.contains(currentId)) {
                excludeIds.add(currentId);
            }

            // 查当前题主表信息（subject_id / question_type）
            LambdaQueryWrapper<BizQuestion> curWrapper = new LambdaQueryWrapper<>();
            curWrapper.eq(BizQuestion::getId, currentId)
                      .ne(BizQuestion::getStatus, "2")
                      .last("LIMIT 1");
            BizQuestion current = bizQuestionMapper.selectOne(curWrapper);
            if (current == null) {
                return null;
            }

            String subjectId = current.getSubjectId();
            Integer questionType = current.getQuestionType();

            // 取当前题首考点（biz_question_knowledge source='U' 按 id ASC 首条 knowledgeId）
            List<QuestionKnowledgeVo> currentKnowledges =
                loadKnowledgesByQuestionIds(Collections.singletonList(currentId), "U")
                    .getOrDefault(currentId, Collections.emptyList());
            String firstKnowledgeId = currentKnowledges.isEmpty()
                ? null : currentKnowledges.get(0).getKnowledgeId();

            // 步骤 1：优先 — 同 subject + 同首考点 + 同 question_type + NOT IN excludeIds
            // 🔴 PRD-A-012 T2 B-1：原 inSql 字符串拼接 SQL 注入风险，改预查 + .in() 参数化
            Long candidateId = null;
            if (firstKnowledgeId != null && subjectId != null && questionType != null) {
                // 预查含同一首考点（source='U'）的题 ID 集合 — 参数化 LambdaQueryWrapper
                List<Long> knowledgeQuestionIds = bizQuestionKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<BizQuestionKnowledge>()
                        .eq(BizQuestionKnowledge::getKnowledgeId, firstKnowledgeId)
                        .eq(BizQuestionKnowledge::getSource, "U")
                        .select(BizQuestionKnowledge::getQuestionId)
                ).stream().map(BizQuestionKnowledge::getQuestionId).distinct().collect(Collectors.toList());

                if (!knowledgeQuestionIds.isEmpty()) {
                    LambdaQueryWrapper<BizQuestion> w1 = new LambdaQueryWrapper<>();
                    w1.eq(BizQuestion::getSubjectId, subjectId)
                      .eq(BizQuestion::getQuestionType, questionType)
                      .eq(BizQuestion::getStatus, "1")
                      .notIn(BizQuestion::getId, excludeIds)
                      // 参数化 .in() — 替代旧 inSql 字符串拼接
                      .in(BizQuestion::getId, knowledgeQuestionIds)
                      .last("LIMIT 1");
                    BizQuestion candidate = bizQuestionMapper.selectOne(w1);
                    if (candidate != null) {
                        candidateId = candidate.getId();
                    }
                }
                // knowledgeQuestionIds 为空 → 无同首考点候选，跳到步骤 2 兜底
            }

            // 步骤 2：兜底 — 同 subject + 同 question_type + NOT IN excludeIds
            if (candidateId == null && subjectId != null && questionType != null) {
                LambdaQueryWrapper<BizQuestion> w2 = new LambdaQueryWrapper<>();
                w2.eq(BizQuestion::getSubjectId, subjectId)
                  .eq(BizQuestion::getQuestionType, questionType)
                  .eq(BizQuestion::getStatus, "1")
                  .notIn(BizQuestion::getId, excludeIds)
                  .last("LIMIT 1");
                BizQuestion candidate = bizQuestionMapper.selectOne(w2);
                if (candidate != null) {
                    candidateId = candidate.getId();
                }
            }

            // 无候选 → 返 null（FE 提示"暂无可替换同类题"）
            if (candidateId == null) {
                return null;
            }

            // 复用 listByIds 同款装配（knowledges + freeTags 完整回填）
            List<QuestionDetailVo> result = listByIds(Collections.singletonList(candidateId));
            return result.isEmpty() ? null : result.get(0);
        }));
    }

    /**
     * 组卷草稿 — section 顺序固定 1=选择 → 4=填空 → 5=简答（misikt 真实行为）。
     *
     * <p>实现策略：复用 {@link IQuestionBasketService#queryBasket}（已带 status='1' 过滤
     * + add_time 倒序 + questionKnowledges 回填），按 questionType 分组装 sections。
     */
    @Override
    public ExamDataVo genExamData(Long userId) {
        ExamDataVo vo = new ExamDataVo();
        if (userId == null) {
            vo.setSections(Collections.emptyList());
            return vo;
        }
        List<QuestionItemVo> basket = questionBasketService.queryBasket(userId);
        if (basket == null || basket.isEmpty()) {
            vo.setSections(Collections.emptyList());
            return vo;
        }

        // 按 questionType 分组（保留 add_time 倒序 — basket 已排）
        Map<Integer, List<QuestionItemVo>> byType = new LinkedHashMap<>();
        for (QuestionItemVo q : basket) {
            if (q.getQuestionType() == null) {
                continue;
            }
            byType.computeIfAbsent(q.getQuestionType(), k -> new ArrayList<>()).add(q);
        }

        // 按 misikt 真实题型顺序 1 → 4 → 5 生成 sections；不存在的题型跳过
        List<ExamSectionVo> sections = new ArrayList<>(3);
        addSectionIfPresent(sections, byType, 1, "一、选择题");
        addSectionIfPresent(sections, byType, 4, "二、填空题");
        addSectionIfPresent(sections, byType, 5, "三、简答题");

        vo.setSections(sections);
        return vo;
    }

    /**
     * PRD-C-007 T1 — 题目 5 维度打标回写。
     *
     * <p>🔴 数据权限坑（PRD-A-002 致命实战）：biz_question 老题 create_user=2（misikt 复刻），
     * 登录 id≠create_by → RuoYi 数据权限拦截器注入 {@code AND create_by=登录id} → 0 行静默假成功。
     * 故全程 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹
     * （biz_question 无 tenant_id，同 replaceQuestion 处理；BaseMapper 继承方法走线程级 ignore）。
     *
     * <p>仅 {@code set} 打标列，不碰题干/答案。labeled_at 取 now（不收 body）。
     *
     * <p>🔴 V905 schema 收敛：dim3_skill / aux_tags 列已 DROP（思维方法砍维 / 血缘并入 ai 表），
     * 故本方法不再写这两列；bo 上对应入参（探针期遗留）暂忽略，待 toolkit 同步。
     */
    @Override
    public void updateLabel(UpdateLabelBo bo) {
        if (bo == null || bo.getQuestionId() == null) {
            return;
        }

        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            LambdaUpdateWrapper<BizQuestion> uw = new LambdaUpdateWrapper<>();
            uw.eq(BizQuestion::getId, bo.getQuestionId())
              .set(BizQuestion::getDim1KpId, bo.getDim1KpId())
              .set(BizQuestion::getDim2Qtype, bo.getDim2Qtype())
              .set(BizQuestion::getDim4Difficulty, bo.getDim4Difficulty())
              .set(BizQuestion::getDim5Structure, bo.getDim5Structure())
              .set(BizQuestion::getLabelStatus, bo.getLabelStatus())
              .set(BizQuestion::getLabelConfidence, bo.getLabelConfidence())
              .set(BizQuestion::getLabeledBy, bo.getLabeledBy())
              .set(BizQuestion::getLabeledAt, new Date());
            // mapper.update(null, wrapper)：entity 传 null，全部更新列走 wrapper.set 显式指定
            bizQuestionMapper.update(null, uw);
            return null;
        }));
    }

    /**
     * PRD-C-009 — teacher 侧录题落库。
     *
     * <p>归属 OWNER 范式（复刻 {@code PaperLibraryServiceImpl.doCreateExamPaper}）：
     * create_user/create_by 一律取 {@code LoginHelper.getUserId()}，绝不信前端 body。
     *
     * <p>外置文本写法（核心，复刻 biz_text_content (question_id, content_type) UNIQUE 一题一类型一行）：
     * <ol>
     *   <li>先 insert(question) 拿 questionId（雪花全局 ASSIGN_ID 自动回填）</li>
     *   <li>对 stem(必)/answer/analyze(各非空才写) 逐个 insert BizTextContent，拿各行 id</li>
     *   <li>updateById 回写 stem/answer/analyzeTextContentId 三个 FK</li>
     * </ol>
     *
     * <p>🔴 全事务体 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹
     * （biz_question / biz_text_content 无 tenant_id；老题 create_user≠登录 id）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDetailVo create(CreateQuestionBo bo) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录不能录题");
        }
        if (bo == null) {
            throw new ServiceException("录题入参不能为空");
        }
        if (bo.getQuestionType() == null) {
            throw new ServiceException("questionType 不能为空");
        }
        if (StringUtils.isBlank(bo.getStem())) {
            throw new ServiceException("stem 题干不能为空");
        }

        String ownerIdStr = String.valueOf(currentUserId);
        Date now = new Date();

        Long newQuestionId = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // 1. INSERT biz_question（OWNER 强制，status='1' 已发布，stem_text 老字段不写）
            BizQuestion q = new BizQuestion();
            q.setQuestionType(bo.getQuestionType());
            // 🔴 2026-06-17：difficult(老列 NOT NULL 无默认) 回退 dim4Difficulty(新难度维)，都缺给中位 2。
            //   母题入库(PRD-C-017)FE 只发 dim4Difficulty 不发 difficult → 老列空 → insert NOT NULL 违约
            //   (500「发生未知异常」)。此前 FE 一直被「题面尚未产出」拦在前面，未触达本插入路径，故隐藏至今。
            Integer difficult = bo.getDifficult() != null ? bo.getDifficult() : bo.getDim4Difficulty();
            q.setDifficult(difficult != null ? difficult : 2);
            q.setSubjectId(bo.getSubjectId());
            q.setStemImgUrl(bo.getStemImg());
            q.setAnswerImgUrl(bo.getAnswerImg());
            q.setExplainImgUrl(bo.getExplainImg());
            q.setFileBinUrl(bo.getFileBin());
            q.setExamYear(bo.getExamYear());
            q.setExamPaperId(bo.getExamPaperId());
            q.setExamPaperName(bo.getExamPaperName());
            // AI 血缘 / 来源（零 DDL，复用 B-012 V11+V16 列）
            q.setMotherQuestionId(bo.getMotherQuestionId());
            q.setVariantRelation(bo.getVariantRelation());
            q.setImportSource(StringUtils.isBlank(bo.getImportSource())
                ? DEFAULT_IMPORT_SOURCE : bo.getImportSource());
            // 5 维度打标（复用 V16 列，同 UpdateLabelBo；与 stem 一并入库时可带）
            // 🔴 V905 schema 收敛：dim3_skill / aux_tags 已 DROP，bo 上对应入参暂忽略（待 toolkit 同步）。
            q.setDim1KpId(bo.getDim1KpId());
            q.setDim2Qtype(bo.getDim2Qtype());
            q.setDim4Difficulty(bo.getDim4Difficulty());
            q.setDim5Structure(bo.getDim5Structure());
            q.setLabelStatus(bo.getLabelStatus());
            q.setLabelConfidence(bo.getLabelConfidence());
            q.setLabeledBy(bo.getLabeledBy());
            // 打标时间 + 标注版本（与 ai 表对齐；labelStatus 带值即视为已标，labeled_at 取 now）
            if (bo.getLabelStatus() != null) {
                q.setLabeledAt(now);
            }
            q.setAnnotateVersion(DEFAULT_ANNOTATE_VERSION);
            // 系统列：服务端强制（绝不信前端 createBy/createUser/status/id）
            q.setVersion(DEFAULT_QUESTION_VERSION);
            q.setStatus("1");
            q.setCreateUser(currentUserId);
            q.setCreateBy(ownerIdStr);
            q.setCreateTime(now);
            q.setUpdateBy(ownerIdStr);
            q.setUpdateTime(now);
            bizQuestionMapper.insert(q);
            Long qid = q.getId();

            // 2. 外置三要素长文本：stem 必写，answer/analyze 各非空才写；拿各行 id 回填 FK
            Long stemTcId = insertTextContent(qid, "S", bo.getStem());
            Long answerTcId = StringUtils.isBlank(bo.getAnswer())
                ? null : insertTextContent(qid, "A", bo.getAnswer());
            Long analyzeTcId = StringUtils.isBlank(bo.getAnalyze())
                ? null : insertTextContent(qid, "E", bo.getAnalyze());

            // 3. 回写三个外置 FK 到 biz_question
            BizQuestion fkUpdate = new BizQuestion();
            fkUpdate.setId(qid);
            fkUpdate.setStemTextContentId(stemTcId);
            fkUpdate.setAnswerTextContentId(answerTcId);
            fkUpdate.setAnalyzeTextContentId(analyzeTcId);
            bizQuestionMapper.updateById(fkUpdate);

            // 4. biz_question_knowledge：主 kp 1 行（is_primary=1）+ 副 kp 各 1 行（is_primary=0）
            //    PRD-C-014 B1 ②；个人题库知识点栏数据源。dim1KpId 有值才写主 kp（不造假行）。
            writeKnowledges(qid, bo, now);

            // 5. 标签三轨：biz_free_tag(exact 复用/新插) + biz_question_free_tag(position=下标)
            //    PRD-C-014 B1 ③；tags 空整段跳过。
            writeFreeTags(qid, bo);

            // 6. biz_question_ai 一行：核心 DNA + 锚定审计 + label 元
            //    PRD-C-014 B1 ④；单项缺值时行仍写、该列留空。
            writeQuestionAi(qid, bo, now);

            return qid;
        }));

        // 读回详情（含外置文本 COALESCE + knowledges + freeTags）
        return selectById(newQuestionId);
    }

    /**
     * PRD-C-015 批4·缺口10 —— teacher 侧「覆盖原行」更新。
     *
     * <p>举一反三「重生后再入库 = 覆盖原行」：按 {@code bo.id} 覆盖一道**已入库**的题，
     * 不新写一行。流程（一个事务，全程 TenantHelper.ignore + DataPermissionHelper.ignore）：
     * <ol>
     *   <li>校验：题存在 + create_user = 登录老师（只许改自己的题，否则 ServiceException）</li>
     *   <li>UPDATE biz_question 直列（题型/难度/科目/imgs/血缘/打标列，update_by/time 刷新，
     *       create_user/create_by/status 不动）</li>
     *   <li>题面三要素 biz_text_content：删旧 S/A/E 三行 → 重插（stem 必，answer/analyze 各非空才写）
     *       → 回写三个 FK</li>
     *   <li>子表 knowledge / free_tag / ai：各按 question_id 删旧 → 复用 create 的 write* 重写（幂等）</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDetailVo update(UpdateQuestionBo bo) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录不能改题");
        }
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("覆盖更新须指定要更新哪道题（id 不能为空）");
        }
        if (bo.getQuestionType() == null) {
            throw new ServiceException("questionType 不能为空");
        }
        if (StringUtils.isBlank(bo.getStem())) {
            throw new ServiceException("stem 题干不能为空");
        }

        Long qid = bo.getId();
        String ownerIdStr = String.valueOf(currentUserId);
        Date now = new Date();

        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // 1. 归属校验：题存在 + create_user = 登录老师（只许改自己的题）
            BizQuestion existing = bizQuestionMapper.selectById(qid);
            if (existing == null || "2".equals(existing.getStatus())) {
                throw new ServiceException("要更新的题不存在或已删除（id=" + qid + "）");
            }
            if (existing.getCreateUser() == null || !existing.getCreateUser().equals(currentUserId)) {
                throw new ServiceException("无权更新这道题（只能改自己的题）");
            }

            // 2. UPDATE biz_question 直列（create_user/create_by/status 不动；update_by/time 刷新）
            BizQuestion q = new BizQuestion();
            q.setId(qid);
            q.setQuestionType(bo.getQuestionType());
            q.setDifficult(bo.getDifficult());
            q.setSubjectId(bo.getSubjectId());
            q.setStemImgUrl(bo.getStemImg());
            q.setAnswerImgUrl(bo.getAnswerImg());
            q.setExplainImgUrl(bo.getExplainImg());
            q.setFileBinUrl(bo.getFileBin());
            q.setExamYear(bo.getExamYear());
            q.setExamPaperId(bo.getExamPaperId());
            q.setExamPaperName(bo.getExamPaperName());
            q.setMotherQuestionId(bo.getMotherQuestionId());
            q.setVariantRelation(bo.getVariantRelation());
            if (StringUtils.isNotBlank(bo.getImportSource())) {
                q.setImportSource(bo.getImportSource());
            }
            q.setDim1KpId(bo.getDim1KpId());
            q.setDim2Qtype(bo.getDim2Qtype());
            q.setDim4Difficulty(bo.getDim4Difficulty());
            q.setDim5Structure(bo.getDim5Structure());
            q.setLabelStatus(bo.getLabelStatus());
            q.setLabelConfidence(bo.getLabelConfidence());
            q.setLabeledBy(bo.getLabeledBy());
            if (bo.getLabelStatus() != null) {
                q.setLabeledAt(now);
            }
            q.setUpdateBy(ownerIdStr);
            q.setUpdateTime(now);
            bizQuestionMapper.updateById(q);

            // 3. 题面三要素：删旧 S/A/E → 重插 → 回写 FK（先清后写，幂等覆盖）
            LambdaUpdateWrapper<BizTextContent> tcDel = new LambdaUpdateWrapper<>();
            tcDel.eq(BizTextContent::getQuestionId, qid);
            bizTextContentMapper.delete(tcDel);
            Long stemTcId = insertTextContent(qid, "S", bo.getStem());
            Long answerTcId = StringUtils.isBlank(bo.getAnswer())
                ? null : insertTextContent(qid, "A", bo.getAnswer());
            Long analyzeTcId = StringUtils.isBlank(bo.getAnalyze())
                ? null : insertTextContent(qid, "E", bo.getAnalyze());
            BizQuestion fkUpdate = new BizQuestion();
            fkUpdate.setId(qid);
            fkUpdate.setStemTextContentId(stemTcId);
            fkUpdate.setAnswerTextContentId(answerTcId);
            fkUpdate.setAnalyzeTextContentId(analyzeTcId);
            bizQuestionMapper.updateById(fkUpdate);

            // 4. 子表先清后写（幂等覆盖）：knowledge / free_tag rel / ai
            LambdaUpdateWrapper<BizQuestionKnowledge> kDel = new LambdaUpdateWrapper<>();
            kDel.eq(BizQuestionKnowledge::getQuestionId, qid);
            bizQuestionKnowledgeMapper.delete(kDel);
            writeKnowledges(qid, bo, now);

            bizFreeTagWriteMapper.deleteRelByQuestion(qid);
            writeFreeTags(qid, bo);

            LambdaUpdateWrapper<BizQuestionAi> aiDel = new LambdaUpdateWrapper<>();
            aiDel.eq(BizQuestionAi::getQuestionId, qid);
            bizQuestionAiMapper.delete(aiDel);
            writeQuestionAi(qid, bo, now);

            return null;
        }));

        return selectById(qid);
    }

    /**
     * 录题工具：插一行 biz_text_content（question_id + content_type 唯一），返回新行 id。
     */
    private Long insertTextContent(Long questionId, String contentType, String content) {
        BizTextContent tc = new BizTextContent();
        tc.setQuestionId(questionId);
        tc.setContentType(contentType);
        tc.setContent(content);
        bizTextContentMapper.insert(tc);
        return tc.getId();
    }

    /**
     * PRD-C-014 B1 ② —— 写 biz_question_knowledge（主 kp is_primary=1 + 副 kp is_primary=0）。
     *
     * <p>规则：
     * <ul>
     *   <li>dim1KpId 有值才写主 kp 1 行（knowledge_id=dim1KpId, is_primary=1, source='U'）；无值不写（不造假行）</li>
     *   <li>副 kp 每个 1 行（is_primary=0）；与主 kp 重复时跳过；同 (questionId, knowledgeId) 去重</li>
     *   <li>越界副 kp（超过 {@link #MAX_SECONDARY_KP}）丢弃</li>
     *   <li>source 统一 'U'（用户/AI 标注，与 V905 副 kp 并入一致）</li>
     * </ul>
     * 防重（⑤）：本方法为新建题，question_id 全新无历史挂接，仅本批内去重即可。
     */
    private void writeKnowledges(Long questionId, CreateQuestionBo bo, Date now) {
        Set<String> seen = new java.util.HashSet<>();

        // 主 kp
        String primaryKp = StringUtils.isBlank(bo.getDim1KpId()) ? null : bo.getDim1KpId().trim();
        if (primaryKp != null) {
            insertKnowledge(questionId, primaryKp, 1, now);
            seen.add(primaryKp);
        }

        // 副 kp（≤3，去重，与主 kp 重复跳过）
        List<Long> secondary = bo.getSecondaryKpIds();
        if (secondary != null && !secondary.isEmpty()) {
            int written = 0;
            for (Long kp : secondary) {
                if (kp == null || written >= MAX_SECONDARY_KP) {
                    continue;
                }
                String kpStr = String.valueOf(kp);
                if (seen.contains(kpStr)) {
                    continue;
                }
                insertKnowledge(questionId, kpStr, 0, now);
                seen.add(kpStr);
                written++;
            }
        }
    }

    /** 插一行 biz_question_knowledge（source='U'）。 */
    private void insertKnowledge(Long questionId, String knowledgeId, int isPrimary, Date now) {
        BizQuestionKnowledge k = new BizQuestionKnowledge();
        k.setQuestionId(questionId);
        k.setKnowledgeId(knowledgeId);
        k.setSource("U");
        k.setIsPrimary(isPrimary);
        k.setCreateTime(now);
        bizQuestionKnowledgeMapper.insert(k);
    }

    /**
     * PRD-C-014 B1 ③ —— 标签三轨写入。
     *
     * <p>每个 tag：biz_free_tag 按 name exact 查既有 id（命中 use_count+1）/ 未命中插新（use_count=1）
     * → biz_question_free_tag (question_id, tag_id, position=数组下标)。tags 空整段跳过。
     *
     * <p>防重（⑤）：同 (questionId, tagId) 已挂接则跳过（B3 单题入库防重依赖）；
     * 同一 tags 数组内重复 name 也去重（避免同题同 tag 多 position 行）。
     */
    private void writeFreeTags(Long questionId, CreateQuestionBo bo) {
        List<String> tags = bo.getTags();
        if (tags == null || tags.isEmpty()) {
            return;
        }
        Set<Long> seenTagIds = new java.util.HashSet<>();
        int position = 0;
        for (String raw : tags) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String name = raw.trim();
            // exact 查既有 id；命中 use_count+1，未命中插新后再查拿 id
            Long tagId = bizFreeTagWriteMapper.selectIdByName(name);
            if (tagId != null) {
                bizFreeTagWriteMapper.incrementUseCount(tagId);
            } else {
                bizFreeTagWriteMapper.insertFreeTag(name);
                tagId = bizFreeTagWriteMapper.selectIdByName(name);
                if (tagId == null) {
                    // 理论不达（unique 保证）；防御性跳过该 tag，不阻断事务
                    continue;
                }
            }
            // 同题同 tag 去重（本批内 + 已存在的）
            if (seenTagIds.contains(tagId) || bizFreeTagWriteMapper.countRel(questionId, tagId) > 0) {
                continue;
            }
            bizFreeTagWriteMapper.insertRel(questionId, tagId, position);
            seenTagIds.add(tagId);
            position++;
        }
    }

    /**
     * PRD-C-014 B1 ④ —— 写 biz_question_ai 一行（核心 DNA + 锚定审计 + label 元）。
     *
     * <p>命名映射：skeleton→solution_skeleton / scene→scenario / examType→assessment_type /
     * hardPoints→breakthrough_points(JSON 数组串) + hard_point_count(代码算 size())。
     * confidence ← labelConfidence。单项缺值时行仍写、该列留空。
     */
    private void writeQuestionAi(Long questionId, CreateQuestionBo bo, Date now) {
        BizQuestionAi ai = new BizQuestionAi();
        ai.setQuestionId(questionId);
        ai.setAnnotateVersion(DEFAULT_ANNOTATE_VERSION);
        ai.setSolutionSkeleton(StringUtils.isBlank(bo.getSkeleton()) ? null : bo.getSkeleton());
        ai.setScenario(StringUtils.isBlank(bo.getScene()) ? null : bo.getScene());
        ai.setAssessmentType(StringUtils.isBlank(bo.getExamType()) ? null : bo.getExamType());

        // 难点：count = 代码算 size()（不信调用方自报）；breakthrough_points = JSON 数组串
        List<String> hardPoints = bo.getHardPoints();
        if (hardPoints != null && !hardPoints.isEmpty()) {
            ai.setHardPointCount(hardPoints.size());
            ai.setBreakthroughPoints(JsonUtils.toJsonString(hardPoints));
        } else {
            ai.setHardPointCount(0);
            ai.setBreakthroughPoints(null);
        }

        // 锚定溯源审计
        ai.setAnchorId(StringUtils.isBlank(bo.getAnchorId()) ? null : bo.getAnchorId());
        ai.setConfidence(bo.getLabelConfidence());
        ai.setNeedAnchorReview(Boolean.TRUE.equals(bo.getNeedAnchorReview()) ? 1 : 0);
        ai.setReasoning(StringUtils.isBlank(bo.getReasoning()) ? null : bo.getReasoning());

        // 打标元
        ai.setLabelStatus(bo.getLabelStatus());
        ai.setLabeledBy(bo.getLabeledBy());
        if (bo.getLabelStatus() != null) {
            ai.setLabeledAt(now);
        }
        ai.setCreateTime(now);

        bizQuestionAiMapper.insert(ai);
    }

    /**
     * PRD-C-014 B1 T4 —— 某 kp 下高频标签候选池。
     *
     * <p>biz_question_free_tag ⨝ biz_question_knowledge（同 question_id 且 knowledge_id=kpId）⨝ biz_free_tag，
     * 按 tag 聚合 count desc limit N。kpId 空/空白返空 list；limit 兜底 300、clamp 1~1000。
     *
     * <p>只读聚合查询，不涉数据权限（三张表均无 create_by 过滤），无需 ignore 包裹；
     * mapper 自带 @InterceptorIgnore 关多租户。
     */
    @Override
    public List<KpTagStatVo> tagsByKp(String kpId, Integer limit) {
        if (StringUtils.isBlank(kpId)) {
            return Collections.emptyList();
        }
        int n = limit == null ? DEFAULT_TAGS_LIMIT : limit;
        if (n < 1) {
            n = DEFAULT_TAGS_LIMIT;
        }
        if (n > MAX_TAGS_LIMIT) {
            n = MAX_TAGS_LIMIT;
        }
        List<Map<String, Object>> rows = bizFreeTagWriteMapper.selectTagsByKp(kpId.trim(), n);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<KpTagStatVo> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            KpTagStatVo vo = new KpTagStatVo();
            Object id = row.get("id");
            Object name = row.get("name");
            Object count = row.get("cnt");
            vo.setId(id == null ? null : ((Number) id).longValue());
            vo.setName(name == null ? null : String.valueOf(name));
            vo.setCount(count == null ? 0L : ((Number) count).longValue());
            result.add(vo);
        }
        return result;
    }

    /**
     * 工具方法：题型有题才加 section。
     */
    private void addSectionIfPresent(List<ExamSectionVo> sections,
                                     Map<Integer, List<QuestionItemVo>> byType,
                                     Integer questionType,
                                     String title) {
        List<QuestionItemVo> qs = byType.get(questionType);
        if (qs == null || qs.isEmpty()) {
            return;
        }
        ExamSectionVo section = new ExamSectionVo();
        section.setTitle(title);
        section.setQuestionType(questionType);
        section.setQuestions(qs);
        sections.add(section);
    }

    /**
     * 构造 page 查询 WHERE 条件。
     *
     * <p>条件构造铁则：
     * <ul>
     *   <li>{@code status='1'} 硬编码（V0.1 只返已发布题；草稿 / 软删隐式过滤）</li>
     *   <li>subjectId 空或 "0" 不过滤</li>
     *   <li>{@code notUsedQuestion=1} 才加 NOT IN biz_paper_question 子查询</li>
     *   <li>{@code notTaskQuestion=1} 加 NOT IN biz_task_question 子查询（V0.1 表暂无数据，结果等同 0）</li>
     * </ul>
     */
    private QueryWrapper<BizQuestion> buildPageWrapper(QuestionPageBo bo) {
        QueryWrapper<BizQuestion> w = new QueryWrapper<>();
        // J 卡 hotfix（2026-05-22）：LEFT JOIN biz_question_favorite f 后，
        // id / create_time 列在 q 和 f 都存在，所有 column 引用必须带 q. 前缀避免 ambiguous。
        w.eq("q.status", "1");

        if (bo.getSubjectId() != null && !bo.getSubjectId().isEmpty() && !"0".equals(bo.getSubjectId())) {
            // ⚠️ BUG-2 真修（2026-05-21）：题↔章节关联走 biz_question_knowledge.knowledge_id，
            // 不要走 biz_question.subject_id（那是卷库分类编码，跟 biz_subject id 体系不一致）。
            // 见 数据建模/07-补充资料/W-6-章节树数据复刻方案-2026-05-21.md §4.1。
            //
            // SQL 注入防护：biz_subject.id 是数字串（4-15 位），强校验 ^\d+$ 拒非法输入。
            // 🔴 PRD-A-012 T2 B-1：原 inSql 字符串拼接虽有白名单挡，但仍走参数化更彻底。
            String sid = bo.getSubjectId();
            if (!sid.matches("^\\d+$")) {
                // 非法 subjectId 直接返空集（不报错，misikt 真站也吞）
                w.apply("1=0");
            } else {
                // 预查含该 knowledge_id 前缀（章节树前缀匹配）的 question_id 集合 — 参数化 likeRight
                List<Long> subjectQuestionIds = bizQuestionKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<BizQuestionKnowledge>()
                        .likeRight(BizQuestionKnowledge::getKnowledgeId, sid)
                        .select(BizQuestionKnowledge::getQuestionId)
                ).stream().map(BizQuestionKnowledge::getQuestionId).distinct().collect(Collectors.toList());
                if (subjectQuestionIds.isEmpty()) {
                    w.apply("1=0");
                } else {
                    w.in("q.id", subjectQuestionIds);
                }
            }
        }
        if (bo.getQuestionType() != null) {
            w.eq("q.question_type", bo.getQuestionType());
        }
        if (bo.getDifficult() != null) {
            w.eq("q.difficult", bo.getDifficult());
        }
        if (bo.getKeyWord() != null && !bo.getKeyWord().isEmpty()) {
            // V0.1 LIKE 兜底（ngram fulltext 未配 my.cnf）
            w.like("q.stem_text", bo.getKeyWord());
        }
        if (bo.getNotUsedQuestion() != null && bo.getNotUsedQuestion() == 1) {
            w.notInSql("q.id", "SELECT question_id FROM biz_paper_question");
        }
        // notTaskQuestion=1：V0.1 schema 无 biz_task_question 表（M6 才上），
        // 入参收下但不施加过滤 — 等同 0=不限。撞 M6 起卡时再补 SQL。
        // if (bo.getNotTaskQuestion() != null && bo.getNotTaskQuestion() == 1) { ... }
        w.orderByDesc("q.create_time").orderByDesc("q.id");
        return w;
    }

    /**
     * 批量按 question_id + source 拉 knowledges 并按 questionId 分组。
     *
     * @param questionIds 题目 ID 集合（空集合返空 Map）
     * @param source      'U' 或 'S'
     */
    private Map<Long, List<QuestionKnowledgeVo>> loadKnowledgesByQuestionIds(Collection<Long> questionIds,
                                                                              String source) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<QuestionKnowledgeVo> all = bizQuestionKnowledgeMapper.selectByQuestionIdsAndSource(questionIds, source);
        if (all == null || all.isEmpty()) {
            return Collections.emptyMap();
        }
        return all.stream().collect(Collectors.groupingBy(QuestionKnowledgeVo::getQuestionId));
    }

    /**
     * 批量按 question_id 拉 freeTags 并按 questionId 分组（X 卡段②）。
     *
     * <p>Mapper 返回 {@link BizQuestionFreeTagMapper.FreeTagWithQid} 含 questionId，
     * 本方法把每行复制成纯 {@link FreeTagVo}（避免 Jackson 序列化时把 questionId
     * 也写进响应 JSON — 子类的额外字段也会被序列化）。
     *
     * @param questionIds 题目 ID 集合（空集合返空 Map）
     */
    private Map<Long, List<FreeTagVo>> loadFreeTagsByQuestionIds(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BizQuestionFreeTagMapper.FreeTagWithQid> all =
            bizQuestionFreeTagMapper.selectGroupedByQuestionIds(questionIds);
        if (all == null || all.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<FreeTagVo>> grouped = new java.util.HashMap<>();
        for (BizQuestionFreeTagMapper.FreeTagWithQid row : all) {
            FreeTagVo pure = new FreeTagVo();
            pure.setId(row.getId());
            pure.setName(row.getName());
            pure.setPosition(row.getPosition());
            grouped.computeIfAbsent(row.getQuestionId(), k -> new ArrayList<>()).add(pure);
        }
        return grouped;
    }
}
