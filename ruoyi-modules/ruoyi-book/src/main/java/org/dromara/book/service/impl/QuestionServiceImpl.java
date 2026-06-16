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
import org.dromara.book.domain.entity.BizQuestionBlock;
import org.dromara.book.domain.entity.BizQuestionKnowledge;
import org.dromara.book.domain.entity.BizTextContent;
import org.dromara.book.domain.vo.ExamDataVo;
import org.dromara.book.domain.vo.ExamSectionVo;
import org.dromara.book.domain.vo.FreeTagVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.book.mapper.BizQuestionBlockMapper;
import org.dromara.book.mapper.BizQuestionFreeTagMapper;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizTextContentMapper;
import org.dromara.book.service.IQuestionBasketService;
import org.dromara.book.service.IQuestionService;
import org.dromara.book.util.BlockJsonValidator;
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
    private final BizTextContentMapper bizTextContentMapper;
    private final BizQuestionBlockMapper bizQuestionBlockMapper;
    private final IQuestionBasketService questionBasketService;

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
        // PRD-A-015：回填结构化网格块 JSON（biz_question_block by question_id；
        // 有则 set，FE 渲染优先用它；null=未结构化，FE 回落旧富文本/图）。
        BizQuestionBlock block = bizQuestionBlockMapper.selectById(id);
        if (block != null) {
            vo.setBlockJson(block.getBlockJson());
        }
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
     * <p>仅 {@code set} 打标列，不碰题干/答案。dim3_skill / aux_tags JSON 列探针期存 JSON 文本
     * （null 时显式 set null，保持可清空语义）。labeled_at 取 now（不收 body）。
     */
    @Override
    public void updateLabel(UpdateLabelBo bo) {
        if (bo == null || bo.getQuestionId() == null) {
            return;
        }

        // JSON 列序列化（探针期存原始 JSON 文本，不上 typeHandler）
        String dim3SkillJson = bo.getDim3Skill() == null ? null : JsonUtils.toJsonString(bo.getDim3Skill());
        String auxTagsJson = bo.getAuxTags() == null ? null : JsonUtils.toJsonString(bo.getAuxTags());

        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            LambdaUpdateWrapper<BizQuestion> uw = new LambdaUpdateWrapper<>();
            uw.eq(BizQuestion::getId, bo.getQuestionId())
              .set(BizQuestion::getDim1KpId, bo.getDim1KpId())
              .set(BizQuestion::getDim2Qtype, bo.getDim2Qtype())
              .set(BizQuestion::getDim3Skill, dim3SkillJson)
              .set(BizQuestion::getDim4Difficulty, bo.getDim4Difficulty())
              .set(BizQuestion::getDim5Structure, bo.getDim5Structure())
              .set(BizQuestion::getAuxTags, auxTagsJson)
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

        // dim3Skill / auxTags JSON 序列化（探针期存原始 JSON 文本，不上 typeHandler，同 updateLabel）
        String dim3SkillJson = bo.getDim3Skill() == null ? null : JsonUtils.toJsonString(bo.getDim3Skill());
        String auxTagsJson = bo.getAuxTags() == null ? null : JsonUtils.toJsonString(bo.getAuxTags());

        Long newQuestionId = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // 1. INSERT biz_question（OWNER 强制，status='1' 已发布，stem_text 老字段不写）
            BizQuestion q = new BizQuestion();
            q.setQuestionType(bo.getQuestionType());
            q.setDifficult(bo.getDifficult());
            q.setSubjectId(bo.getSubjectId());
            q.setStemImgUrl(bo.getStemImg());
            q.setAnswerImgUrl(bo.getAnswerImg());
            q.setExplainImgUrl(bo.getExplainImg());
            q.setFileBinUrl(bo.getFileBin());
            q.setFreeTag(bo.getFreeTag());
            q.setExamYear(bo.getExamYear());
            q.setExamPaperId(bo.getExamPaperId());
            q.setExamPaperName(bo.getExamPaperName());
            // AI 血缘 / 来源（零 DDL，复用 B-012 V11+V16 列）
            q.setMotherQuestionId(bo.getMotherQuestionId());
            q.setVariantRelation(bo.getVariantRelation());
            q.setImportSource(StringUtils.isBlank(bo.getImportSource())
                ? DEFAULT_IMPORT_SOURCE : bo.getImportSource());
            q.setAuxTags(auxTagsJson);
            // 5 维度打标（复用 V16 列，同 UpdateLabelBo；与 stem 一并入库时可带）
            q.setDim1KpId(bo.getDim1KpId());
            q.setDim2Qtype(bo.getDim2Qtype());
            q.setDim3Skill(dim3SkillJson);
            q.setDim4Difficulty(bo.getDim4Difficulty());
            q.setDim5Structure(bo.getDim5Structure());
            q.setLabelStatus(bo.getLabelStatus());
            q.setLabelConfidence(bo.getLabelConfidence());
            q.setLabeledBy(bo.getLabeledBy());
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

            // 4. PRD-A-015：blockJson 非空 → 校验 §10.1 + insert biz_question_block（同一事务）
            if (StringUtils.isNotBlank(bo.getBlockJson())) {
                BlockJsonValidator.validate(bo.getBlockJson());
                Date blockNow = new Date();
                BizQuestionBlock block = new BizQuestionBlock();
                block.setQuestionId(qid);
                block.setBlockJson(bo.getBlockJson());
                block.setV(1);
                block.setUpdateBy(currentUserId);
                block.setCreateTime(blockNow);
                block.setUpdateTime(blockNow);
                bizQuestionBlockMapper.insert(block);
            }

            return qid;
        }));

        // 读回详情（含外置文本 COALESCE + knowledges + freeTags）
        return selectById(newQuestionId);
    }

    /**
     * PRD-A-015 — 题目结构化编辑。
     *
     * <p>权威源 = blockJson。流程：OWNER 校验 → 校验 blockJson → upsert biz_question_block →
     * 可选元数据列 / 外置文本同步。全事务体 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))}
     * 包裹（biz_* 表无 tenant_id + 老题 create_user≠登录 id，同 create() 范式）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDetailVo update(UpdateQuestionBo bo) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录不能编辑题目");
        }
        if (bo == null || bo.getQuestionId() == null) {
            throw new ServiceException("questionId 不能为空");
        }
        if (StringUtils.isBlank(bo.getBlockJson())) {
            throw new ServiceException("blockJson 结构化内容不能为空");
        }

        // 先校验 blockJson（§10.1 schema），非法直接抛，不开事务
        BlockJsonValidator.validate(bo.getBlockJson());

        String ownerIdStr = String.valueOf(currentUserId);
        Date now = new Date();

        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // 1. 加载原题做 OWNER 校验（仍走 DataPermissionHelper.ignore 防数据权限拦截器对老题
            //    create_user≠登录 id 注入 AND create_by 致加载到 null）。
            //    🔴 只取 id+create_user 列裁剪查询，不用 selectById 全列扫：① 校验本就只需这两列；
            //    ② biz_question 实体含 freeTag→free_tag 映射，全列扫在缺该列的库上会 Unknown column 报错
            //    （本 dev 库已漂移丢 free_tag，详见 PRD-A-015 progress.md「预存阻塞」）。
            BizQuestion q = bizQuestionMapper.selectOne(
                new LambdaQueryWrapper<BizQuestion>()
                    .select(BizQuestion::getId, BizQuestion::getCreateUser,
                            BizQuestion::getStemTextContentId, BizQuestion::getAnswerTextContentId,
                            BizQuestion::getAnalyzeTextContentId)
                    .eq(BizQuestion::getId, bo.getQuestionId()));
            if (q == null) {
                throw new ServiceException("题目不存在");
            }

            // 2. OWNER 校验：非本人题（含他人题 + 公共题）一律拒（G5）
            if (q.getCreateUser() == null || !q.getCreateUser().equals(currentUserId)) {
                throw new ServiceException("无权编辑非本人题目");
            }

            // 3. upsert biz_question_block（question_id 为 PK，存在→update 否则 insert）
            BizQuestionBlock existing = bizQuestionBlockMapper.selectById(bo.getQuestionId());
            if (existing == null) {
                BizQuestionBlock block = new BizQuestionBlock();
                block.setQuestionId(bo.getQuestionId());
                block.setBlockJson(bo.getBlockJson());
                block.setV(1);
                block.setUpdateBy(currentUserId);
                block.setCreateTime(now);
                block.setUpdateTime(now);
                bizQuestionBlockMapper.insert(block);
            } else {
                existing.setBlockJson(bo.getBlockJson());
                existing.setV(1);
                existing.setUpdateBy(currentUserId);
                existing.setUpdateTime(now);
                bizQuestionBlockMapper.updateById(existing);
            }

            // 4. 可选元数据：传了才更新 biz_question 对应列（updateById 只更新非 null 字段）
            boolean metaTouched = false;
            BizQuestion metaUpdate = new BizQuestion();
            metaUpdate.setId(bo.getQuestionId());
            if (bo.getQuestionType() != null) {
                metaUpdate.setQuestionType(bo.getQuestionType());
                metaTouched = true;
            }
            if (bo.getDifficult() != null) {
                metaUpdate.setDifficult(bo.getDifficult());
                metaTouched = true;
            }
            if (StringUtils.isNotBlank(bo.getSubjectId())) {
                metaUpdate.setSubjectId(bo.getSubjectId());
                metaTouched = true;
            }
            if (metaTouched) {
                metaUpdate.setUpdateBy(ownerIdStr);
                metaUpdate.setUpdateTime(now);
                bizQuestionMapper.updateById(metaUpdate);
            }

            // 5. 可选外置文本同步：stem/answer/analyze 非空才 upsert biz_text_content；
            //    无该 FK 则新建并回写 FK 到 biz_question（参考 create() 的 insertTextContent）。
            Long stemFk = upsertTextContentIfPresent(bo.getQuestionId(), "S",
                bo.getStem(), q.getStemTextContentId());
            Long answerFk = upsertTextContentIfPresent(bo.getQuestionId(), "A",
                bo.getAnswer(), q.getAnswerTextContentId());
            Long analyzeFk = upsertTextContentIfPresent(bo.getQuestionId(), "E",
                bo.getAnalyze(), q.getAnalyzeTextContentId());
            if (stemFk != null || answerFk != null || analyzeFk != null) {
                BizQuestion fkUpdate = new BizQuestion();
                fkUpdate.setId(bo.getQuestionId());
                fkUpdate.setStemTextContentId(stemFk);
                fkUpdate.setAnswerTextContentId(answerFk);
                fkUpdate.setAnalyzeTextContentId(analyzeFk);
                bizQuestionMapper.updateById(fkUpdate);
            }

            return null;
        }));

        // 读回详情（含 blockJson + 外置文本 + knowledges + freeTags）
        return selectById(bo.getQuestionId());
    }

    /**
     * 编辑工具：内容非空才 upsert 一行 biz_text_content（question_id + content_type 唯一）。
     *
     * <p>已有 FK（existingFkId 非空）→ updateById 改 content，返回该 FK（FK 不变）；
     * 无 FK → insert 新行并返回新 id（供调用方回写 biz_question.*TextContentId）。
     * 内容空白 → 不动，返回原 FK（不新建、不清空，保守语义）。
     *
     * @return 本次应回写到 biz_question 的 FK 值（可能等于原 FK / 新 id / null）
     */
    private Long upsertTextContentIfPresent(Long questionId, String contentType,
                                            String content, Long existingFkId) {
        if (StringUtils.isBlank(content)) {
            return existingFkId;
        }
        if (existingFkId != null) {
            BizTextContent tc = new BizTextContent();
            tc.setId(existingFkId);
            tc.setContent(content);
            bizTextContentMapper.updateById(tc);
            return existingFkId;
        }
        return insertTextContent(questionId, contentType, content);
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
        // 富文本带出卡：按出处试卷 / 打标态可选过滤（非 null 才施加）
        if (bo.getExamPaperId() != null) {
            w.eq("q.exam_paper_id", bo.getExamPaperId());
        }
        if (bo.getLabelStatus() != null) {
            w.eq("q.label_status", bo.getLabelStatus());
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
