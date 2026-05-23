package org.dromara.bookadmin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizQuestionKnowledge;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.book.domain.vo.FreeTagVo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.QuestionKnowledgeVo;
import org.dromara.book.domain.vo.SubjectNodeVo;
import org.dromara.book.mapper.BizQuestionFreeTagMapper;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizSubjectMapper;
import org.dromara.admincommon.service.IAdminFileUploadService;
import org.dromara.bookadmin.domain.bo.AdminQuestionEditBo;
import org.dromara.bookadmin.domain.bo.AdminQuestionPageBo;
import org.dromara.bookadmin.mapper.AdminFreeTagWriteMapper;
import org.dromara.bookadmin.mapper.AdminPaperQuestionRefMapper;
import org.dromara.bookadmin.service.IAdminQuestionService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * admin 题目 Service 实现（H1 卡 V-6 — 写操作统一事务入口）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本实现物理落 {@code ruoyi-book-admin} 模块，
 * 依赖 {@code ruoyi-book} 共享 Mapper / Entity / VO（DDL 单源），但方法内部
 * <strong>直接走 Mapper 自查</strong>，<strong>禁调</strong>
 * {@code org.dromara.book.service.IQuestionService}（教师端 Service）的任何方法。
 *
 * <p>本波（V-5 重构）{@link #adminPage(AdminQuestionPageBo)} 实现策略与教师端
 * {@code QuestionServiceImpl#page(QuestionPageBo)} 当前等效（同 SQL 同回填），
 * 但独立维护 — admin 后续可加 status='0' 草稿过滤 / 创建人过滤 / status='2' 软删可见
 * 等需求，改本方法不影响 teacher 的 page。
 *
 * <p>下波（V-1/V-2/V-3/V-4）在此接管：
 * <ul>
 *   <li>事务边界（@Transactional 写操作必加）</li>
 *   <li>biz_question / biz_question_knowledge（U 轨全量替换）/ biz_question_free_tag
 *       + biz_free_tag 字典 use_count 同步 / biz_question.free_tag 冗余串同步</li>
 *   <li>软删时 biz_paper_question 引用校验（抛 ServiceException）</li>
 *   <li>fileUpload 走 C 卡 OssClient + 写 image_asset（source='admin'）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class AdminQuestionServiceImpl implements IAdminQuestionService {

    private final BizQuestionMapper bizQuestionMapper;
    private final BizQuestionKnowledgeMapper bizQuestionKnowledgeMapper;
    private final BizQuestionFreeTagMapper bizQuestionFreeTagMapper;
    private final BizSubjectMapper bizSubjectMapper;
    private final AdminPaperQuestionRefMapper adminPaperQuestionRefMapper;
    /** admin 自有 freeTag 字典 + 关联表写 Mapper（V-6 波 2b 新增）。 */
    private final AdminFreeTagWriteMapper adminFreeTagWriteMapper;
    /** admin 端通用文件上传 Service（H1 卡补丁抽离 — V-4 fileUpload 委托）。 */
    private final IAdminFileUploadService adminFileUploadService;

    /**
     * Jackson ObjectMapper（V-6 — biz_question.options_json 序列化）。
     * 静态共享避免重复创建；ObjectMapper 是线程安全的（配置不变情况下）。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** misikt 默认每页 10，pageIndex 兜底 1（与教师端一致）。 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_INDEX = 1;

    /** V-4 文件上传：H 卡题库 asset_kind 白名单（image_asset.asset_kind 取值，小写）。 */
    private static final Set<String> ADMIN_UPLOAD_ALLOWED_TYPES =
        new HashSet<>(Arrays.asList("stem", "answer", "explain"));

    /** V-4 文件上传：H 卡题库 image_asset.entity_type 固定值。 */
    private static final String ADMIN_UPLOAD_ENTITY_TYPE = "question";

    /** V-4 文件上传：H 卡题库 image_asset.entity_ref 固定值（admin 直传来源标识）。 */
    private static final String ADMIN_UPLOAD_ENTITY_REF = "admin";

    /** V-4 文件上传：H 卡题库 OSS key 顶层前缀。 */
    private static final String ADMIN_UPLOAD_KEY_PREFIX = "admin-upload";

    /**
     * adminLazyTree 整树缓存（H1 卡 §6 R12 — 编辑页加载性能）。
     * biz_subject ~2116 行，每次内存重建树 + 序列化耗时 100-200ms；admin 改章节树功能未做，
     * 进程内静态缓存 5 分钟 TTL 足够；BE 重启 / TTL 过期自动重拉。
     * 未来 admin 章节编辑功能上线后，写操作处主动清零 LAZY_TREE_CACHE_AT。
     */
    private static final long LAZY_TREE_CACHE_TTL_MS = 5L * 60 * 1000;
    private static volatile List<SubjectNodeVo> LAZY_TREE_CACHE = null;
    private static volatile long LAZY_TREE_CACHE_AT = 0L;

    @Override
    public MisiktPageVo<QuestionItemVo> adminPage(AdminQuestionPageBo bo) {
        int pageIndex = bo.getPageIndex() == null || bo.getPageIndex() <= 0
            ? DEFAULT_PAGE_INDEX : bo.getPageIndex();
        int pageSize = bo.getPageSize() == null || bo.getPageSize() <= 0
            ? DEFAULT_PAGE_SIZE : bo.getPageSize();

        QueryWrapper<BizQuestion> wrapper = buildAdminPageWrapper(bo);

        // 兼容 J 卡段② mapper 签名（另一线程已 commit）：第 3 参数 currentUserId 用于
        // LEFT JOIN biz_question_favorite 出 isFavorite。admin 通道也走 Sa-Token 登录态。
        Long currentUserId = LoginHelper.getUserId();

        Page<QuestionItemVo> mpPage = new Page<>(pageIndex, pageSize);
        IPage<QuestionItemVo> result = bizQuestionMapper.selectQuestionPage(mpPage, wrapper, currentUserId);

        // 回填 questionKnowledges（source='U'） + freeTags（与教师端等效）
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

    /**
     * 构造 admin page 查询 WHERE 条件（独立维护，当前与教师端 buildPageWrapper 等效）。
     *
     * <p>本波（V-5 重构）admin 复用教师端默认行为：
     * <ul>
     *   <li>{@code status='1'} 硬编码（暂只返已发布题；下波 V-2/V-3 接管时放开此限制以便 admin 看草稿/软删）</li>
     *   <li>subjectId 空或 "0" 不过滤；非数字串直接 1=0 兜底</li>
     *   <li>{@code notUsedQuestion=1} 加 NOT IN biz_paper_question 子查询</li>
     * </ul>
     *
     * <p>H1 卡 Bug2 补丁：新增 {@code tagIds} 多选 OR 语义筛选 —
     * {@code WHERE id IN (SELECT DISTINCT question_id FROM biz_question_free_tag WHERE tag_id IN (...))}。
     * tagIds 是 Long 列表，{@code stream.map(String::valueOf)} 安全（不会 SQL 注入）。
     */
    private QueryWrapper<BizQuestion> buildAdminPageWrapper(AdminQuestionPageBo bo) {
        QueryWrapper<BizQuestion> w = new QueryWrapper<>();
        // H1 卡 Bug D 修：admin 列表放开 status 过滤 — FE 状态下拉传 "0"/"1"/"2" 按值过滤；
        // 不传 / 空 → admin 看全部状态（含草稿 '0' / 软删 '2'）。之前硬编码 status='1'
        // 让用户刚新建（'0' 草稿）/ 软删（'2'）的题在列表完全不可见。
        // q. 前缀避免 mapper.xml LEFT JOIN biz_question_favorite 时列歧义防御。
        String statusFilter = bo.getStatus();
        if (statusFilter != null && !statusFilter.isEmpty()) {
            w.eq("q.status", statusFilter);
        }

        if (bo.getSubjectId() != null && !bo.getSubjectId().isEmpty() && !"0".equals(bo.getSubjectId())) {
            // 题↔章节关联走 biz_question_knowledge.knowledge_id（教师端 BUG-2 修复，
            // 数据建模/07-补充资料/W-6-章节树数据复刻方案-2026-05-21.md §4.1）
            String sid = bo.getSubjectId();
            if (!sid.matches("^\\d+$")) {
                w.apply("1=0");
            } else {
                // H1 卡 Bug B 修：mapper.xml LEFT JOIN biz_question_favorite 让 id 列在主表/joined 表都存在，
                // inSql 必须带主表 alias q. 前缀避免 "Column 'id' in IN/ALL/ANY subquery is ambiguous"
                w.inSql("q.id",
                    "SELECT DISTINCT question_id FROM biz_question_knowledge "
                        + "WHERE knowledge_id LIKE '" + sid + "%'");
            }
        }
        if (bo.getQuestionType() != null) {
            w.eq("question_type", bo.getQuestionType());
        }
        if (bo.getDifficult() != null) {
            w.eq("difficult", bo.getDifficult());
        }
        if (bo.getKeyWord() != null && !bo.getKeyWord().isEmpty()) {
            w.like("stem_text", bo.getKeyWord());
        }
        if (bo.getNotUsedQuestion() != null && bo.getNotUsedQuestion() == 1) {
            // 同上，notInSql 第一参也需带 q. 前缀避免 favorite 表 id 列歧义
            w.notInSql("q.id", "SELECT question_id FROM biz_paper_question");
        }
        // H1 卡 Bug2 补丁：admin 独有 tagIds 多选筛选（OR 语义）
        // 同上 — q.id 前缀避免双表 JOIN ambiguous
        if (bo.getTagIds() != null && !bo.getTagIds().isEmpty()) {
            String inList = bo.getTagIds().stream().map(String::valueOf).collect(Collectors.joining(","));
            w.inSql("q.id",
                "SELECT DISTINCT question_id FROM biz_question_free_tag WHERE tag_id IN (" + inList + ")");
        }
        // mapper.xml selectQuestionPage 走 LEFT JOIN biz_question_favorite，主表 alias=q，
        // favorite 表也有 create_time / id 列 → orderBy 必须加 q. 前缀避免 ambiguous。
        w.orderByDesc("q.create_time").orderByDesc("q.id");
        return w;
    }

    @Override
    public QuestionDetailVo adminSelectById(Long id) {
        if (id == null) {
            return null;
        }
        QuestionDetailVo vo = bizQuestionMapper.selectQuestionDetailById(id);
        if (vo == null) {
            return null;
        }
        // 回填 questionKnowledges (U) + questionStdKnowledges (S) + freeTags
        // 与教师端 QuestionServiceImpl#selectById 等效，但 admin 独立维护
        List<Long> ids = Collections.singletonList(id);
        vo.setQuestionKnowledges(loadKnowledgesByQuestionIds(ids, "U").getOrDefault(id, new ArrayList<>()));
        vo.setQuestionStdKnowledges(loadKnowledgesByQuestionIds(ids, "S").getOrDefault(id, new ArrayList<>()));
        vo.setFreeTags(loadFreeTagsByQuestionIds(ids).getOrDefault(id, new ArrayList<>()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminSoftDelete(Long id) {
        if (id == null) {
            throw new ServiceException("题目 ID 不能为空");
        }
        // 题目存在性校验（避免软删一个不存在的 id 静默返 ok）
        BizQuestion exists = bizQuestionMapper.selectById(id);
        if (exists == null) {
            throw new ServiceException("题目不存在: " + id);
        }
        if ("2".equals(exists.getStatus())) {
            throw new ServiceException("题目已软删，不能重复删除");
        }
        // 引用校验 — 走 admin 自有 Mapper（不动 ruoyi-book/BizPaperQuestionMapper）
        int refCount = adminPaperQuestionRefMapper.countByQuestionId(id);
        if (refCount > 0) {
            throw new ServiceException("该题被 " + refCount + " 张试卷引用，无法删除");
        }
        // 软删：UPDATE biz_question SET status='2', update_time=NOW() WHERE id=?
        // 不动 biz_question_knowledge / biz_question_free_tag — 历史试卷渲染知识点 tag 仍可用
        UpdateWrapper<BizQuestion> w = new UpdateWrapper<>();
        w.eq("id", id)
            .set("status", "2")
            .set("update_time", new Date());
        bizQuestionMapper.update(null, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminPublish(Long id) {
        if (id == null) {
            throw new ServiceException("题目 ID 不能为空");
        }
        // SQL: UPDATE biz_question SET status='1', update_time=NOW() WHERE id=? AND status='0'
        // 走 UpdateWrapper 串 status='0' 条件，让 DB 一步完成状态校验 + 写
        UpdateWrapper<BizQuestion> w = new UpdateWrapper<>();
        w.eq("id", id)
            .eq("status", "0")
            .set("status", "1")
            .set("update_time", new Date());
        int affected = bizQuestionMapper.update(null, w);
        if (affected == 0) {
            throw new ServiceException("状态非法（当前不是草稿，不能发布）");
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long adminEdit(AdminQuestionEditBo bo) {
        // ===== Step 0：入参校验（PRD §3.1 + §6 R7） =====
        validateEditBo(bo);

        boolean isCreate = bo.getId() == null;
        Long currentUserId = LoginHelper.getUserId();
        String currentUserName = LoginHelper.getUsername();

        // ===== Step 1：INSERT/UPDATE biz_question（含 free_tag 冗余串同步） =====
        Long questionId;
        if (isCreate) {
            BizQuestion entity = new BizQuestion();
            entity.setQuestionType(bo.getQuestionType());
            entity.setDifficult(bo.getDifficult());
            entity.setSubjectId(bo.getSubjectId());
            entity.setShortTitle(bo.getShortTitle());
            entity.setStemText(bo.getStemText());
            entity.setStemImgUrl(bo.getStemImgUrl());
            entity.setAnswerImgUrl(bo.getAnswerImgUrl());
            entity.setExplainImgUrl(bo.getExplainImgUrl());
            entity.setOptionsJson(serializeOptionsJson(bo.getOptionsJson()));
            entity.setCorrectAnswer(bo.getCorrectAnswer());
            entity.setScoreStdJson(bo.getScoreStdJson());
            entity.setFreeTag(joinFreeTag(bo.getTagNames()));
            // 新建时 status='0' 草稿（PRD §3.8 状态机）；status='1' 走 publish 端点
            entity.setStatus("0");
            // create_by / create_user 手动赋值（entity 没有自动填充注解）
            entity.setCreateBy(currentUserName);
            entity.setCreateUser(currentUserId);
            // create_time / update_time 由 entity @TableField(fill) MetaObjectHandler 自动填充
            bizQuestionMapper.insert(entity);
            questionId = entity.getId();
            if (questionId == null) {
                // 防御：MyBatis-Plus useGeneratedKeys 默认开，理论上一定会回填 id
                throw new ServiceException("新建题目失败：未拿到自增 id");
            }
        } else {
            questionId = bo.getId();
            // 编辑前先确认题目存在 + 未软删（防对 status='2' 的题做编辑导致渲染串）
            BizQuestion exists = bizQuestionMapper.selectById(questionId);
            if (exists == null) {
                throw new ServiceException("题目不存在: " + questionId);
            }
            if ("2".equals(exists.getStatus())) {
                throw new ServiceException("题目已软删，不能编辑");
            }
            // UPDATE — status 不动（要发布走 publish 端点）；update_by 手动赋；update_time 自动填充
            UpdateWrapper<BizQuestion> w = new UpdateWrapper<>();
            w.eq("id", questionId)
                .set("question_type", bo.getQuestionType())
                .set("difficult", bo.getDifficult())
                .set("subject_id", bo.getSubjectId())
                .set("short_title", bo.getShortTitle())
                .set("stem_text", bo.getStemText())
                .set("stem_img_url", bo.getStemImgUrl())
                .set("answer_img_url", bo.getAnswerImgUrl())
                .set("explain_img_url", bo.getExplainImgUrl())
                .set("options_json", serializeOptionsJson(bo.getOptionsJson()))
                .set("correct_answer", bo.getCorrectAnswer())
                .set("score_std_json", bo.getScoreStdJson())
                .set("free_tag", joinFreeTag(bo.getTagNames()))
                .set("update_by", currentUserName)
                .set("update_time", new Date());
            int affected = bizQuestionMapper.update(null, w);
            if (affected == 0) {
                throw new ServiceException("题目更新失败（影响行数 0）: " + questionId);
            }
        }

        // ===== Step 2：知识点全量替换（仅 U 轨；S 轨不动 — 防污染标准库） =====
        writeKnowledgesUOnly(questionId, bo.getQuestionKnowledges());

        // ===== Step 3：标签全量替换（含字典自动建 + 冗余串已在 Step 1 同步） =====
        writeFreeTags(questionId, bo.getTagNames());

        return questionId;
    }

    /**
     * 入参校验（V-1 — PRD §3.1 + §6 R7）。任一不满足抛 ServiceException 整体回滚。
     */
    private void validateEditBo(AdminQuestionEditBo bo) {
        if (bo == null) {
            throw new ServiceException("入参不能为空");
        }
        if (bo.getQuestionType() == null
            || bo.getQuestionType() < 1 || bo.getQuestionType() > 5) {
            throw new ServiceException("题型非法（必须 1..5）");
        }
        if (bo.getDifficult() == null
            || bo.getDifficult() < 1 || bo.getDifficult() > 4) {
            throw new ServiceException("难度非法（必须 1..4）");
        }
        if (bo.getSubjectId() == null || bo.getSubjectId().isEmpty()) {
            throw new ServiceException("章节 ID 不能为空");
        }
        if (bizSubjectMapper.selectById(bo.getSubjectId()) == null) {
            throw new ServiceException("章节不存在: " + bo.getSubjectId());
        }
        // PRD §6 R7：stemText / stemImgUrl 至少有一个非空
        boolean stemTextEmpty = bo.getStemText() == null || bo.getStemText().isEmpty();
        boolean stemImgEmpty = bo.getStemImgUrl() == null || bo.getStemImgUrl().isEmpty();
        if (stemTextEmpty && stemImgEmpty) {
            throw new ServiceException("题干文本与题干图至少需要填一个");
        }
        // 选择题 (type=1)：必须有 ≥ 2 个选项 + correctAnswer ∈ optionsJson[*].key
        if (bo.getQuestionType() == 1) {
            List<Map<String, Object>> options = bo.getOptionsJson();
            if (options == null || options.size() < 2) {
                throw new ServiceException("选择题至少需要 2 个选项");
            }
            if (bo.getCorrectAnswer() == null || bo.getCorrectAnswer().isEmpty()) {
                throw new ServiceException("选择题必须指定正确答案");
            }
            Set<String> keys = new LinkedHashSet<>();
            for (Map<String, Object> opt : options) {
                Object k = opt.get("key");
                if (k != null) {
                    keys.add(String.valueOf(k));
                }
            }
            if (!keys.contains(bo.getCorrectAnswer())) {
                throw new ServiceException("正确答案不在选项 key 列表中: " + bo.getCorrectAnswer());
            }
        }
        // 知识点至少 1 个 + 每个 knowledgeId 在 biz_subject 存在
        List<AdminQuestionEditBo.QuestionKnowledgeItem> kns = bo.getQuestionKnowledges();
        if (kns == null || kns.isEmpty()) {
            throw new ServiceException("至少关联 1 个知识点");
        }
        for (AdminQuestionEditBo.QuestionKnowledgeItem k : kns) {
            if (k.getKnowledgeId() == null || k.getKnowledgeId().isEmpty()) {
                throw new ServiceException("知识点 ID 不能为空");
            }
            if (bizSubjectMapper.selectById(k.getKnowledgeId()) == null) {
                throw new ServiceException("知识点不存在: " + k.getKnowledgeId());
            }
        }
    }

    /**
     * 序列化 optionsJson List → String（落 biz_question.options_json MySQL JSON 列）。
     *
     * @return null（入参 null/空） 或 JSON 字符串
     */
    private String serializeOptionsJson(List<Map<String, Object>> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new ServiceException("optionsJson 序列化失败: " + e.getMessage());
        }
    }

    /**
     * 拼 biz_question.free_tag 冗余串（PRD §3.5）。
     * tagNames 空时返 null（而非空串）— 与 DB 现存数据一致。
     */
    private String joinFreeTag(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return null;
        }
        return String.join(",", tagNames);
    }

    /**
     * U 轨知识点全量替换（V-6 — PRD §3.5）。
     *
     * <p>S 轨（source='S' 标准库）不动 — admin 编辑禁污染标准库。
     */
    private void writeKnowledgesUOnly(Long questionId,
                                      List<AdminQuestionEditBo.QuestionKnowledgeItem> knowledges) {
        // 先删旧 U 轨；S 轨不动
        QueryWrapper<BizQuestionKnowledge> delWrapper = new QueryWrapper<>();
        delWrapper.eq("question_id", questionId).eq("source", "U");
        bizQuestionKnowledgeMapper.delete(delWrapper);

        // 写新 U 轨（去重，防 FE 误传相同 knowledgeId 撞 unique 约束）
        Set<String> seen = new LinkedHashSet<>();
        Date now = new Date();
        for (AdminQuestionEditBo.QuestionKnowledgeItem item : knowledges) {
            String kid = item.getKnowledgeId();
            if (!seen.add(kid)) {
                continue;
            }
            BizQuestionKnowledge row = new BizQuestionKnowledge();
            row.setQuestionId(questionId);
            row.setKnowledgeId(kid);
            row.setSource("U"); // 强制 'U'，忽略 FE 传的 source 字段（PRD §3.5 防污染）
            row.setCreateTime(now);
            bizQuestionKnowledgeMapper.insert(row);
        }
    }

    /**
     * 标签全量替换 + biz_free_tag 字典自动建（V-6 — PRD §3.5 + §6 R5）。
     *
     * <p>头期宽松策略（PRD §6 R5）：INSERT 字典 use_count=1，DELETE 关联时不减 use_count
     * （误差沉远期 cron 重算）。
     *
     * <p>同一题内 tagNames 去重（防 FE 误传重复 name 撞 unique 约束于 biz_question_free_tag 的
     * (question_id, tag_id) 业务唯一性 — 表层无此 unique，但避免重复行也是良好实践）。
     */
    private void writeFreeTags(Long questionId, List<String> tagNames) {
        // 先全删旧关联（biz_question_free_tag）
        adminFreeTagWriteMapper.deleteByQuestionId(questionId);

        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }
        // 去重 + 保序（用 LinkedHashSet）
        Set<String> uniqueNames = new LinkedHashSet<>();
        for (String name : tagNames) {
            if (name != null && !name.isEmpty()) {
                uniqueNames.add(name);
            }
        }
        int position = 0;
        for (String name : uniqueNames) {
            Long tagId = adminFreeTagWriteMapper.selectIdByName(name);
            if (tagId == null) {
                adminFreeTagWriteMapper.insertFreeTag(name);
                tagId = adminFreeTagWriteMapper.selectIdByName(name);
                if (tagId == null) {
                    // 防御：刚 INSERT 完应该一定能查到（unique 约束）
                    throw new ServiceException("freeTag 字典插入后回查失败: " + name);
                }
            }
            adminFreeTagWriteMapper.insertRel(questionId, tagId, position);
            position++;
        }
    }

    @Override
    public List<SubjectNodeVo> adminLazyTree(SubjectLazyTreeBo bo) {
        // V0.1 忽略 bo.parentId（与教师端 SubjectServiceImpl 等效，misikt 真实行为也是一次返整树）
        // H1 §6 R12 性能优化：5 分钟内重复请求走静态缓存（biz_subject 2116 行整树建立耗时大）
        long now = System.currentTimeMillis();
        List<SubjectNodeVo> cached = LAZY_TREE_CACHE;
        if (cached != null && now - LAZY_TREE_CACHE_AT < LAZY_TREE_CACHE_TTL_MS) {
            return cached;
        }

        List<BizSubject> all = bizSubjectMapper.selectList(null);
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 实体 → VO
        Map<String, SubjectNodeVo> idMap = new HashMap<>(all.size() * 2);
        for (BizSubject e : all) {
            idMap.put(e.getId(), toSubjectVo(e));
        }

        // 2. 串父子关系；parent 不在结果集中的视为顶层
        List<SubjectNodeVo> roots = new ArrayList<>();
        for (BizSubject e : all) {
            SubjectNodeVo node = idMap.get(e.getId());
            SubjectNodeVo parent = e.getParentId() == null ? null : idMap.get(e.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }

        // 3. sort 排序 + hasChildren 标记
        sortSubjectRecursive(roots);
        markSubjectHasChildren(roots);

        LAZY_TREE_CACHE = roots;
        LAZY_TREE_CACHE_AT = now;
        return roots;
    }

    /**
     * BUG-1 兜底（W-6 数据已大幅清洗，但保留以防新增"节点 XXXX"漏网）。
     */
    private String resolveSubjectName(BizSubject e) {
        String name = e.getName();
        if (name == null || !name.matches("^节点 \\d+$")) {
            return name;
        }
        Integer level = e.getLevel();
        String prefix = level == null ? "节点 " : switch (level) {
            case 1 -> "学科 ";
            case 2 -> "教材 ";
            case 3 -> "章节 ";
            case 4 -> "节 ";
            case 5 -> "知识点 ";
            default -> "节点 ";
        };
        return prefix + e.getId();
    }

    private SubjectNodeVo toSubjectVo(BizSubject e) {
        SubjectNodeVo vo = new SubjectNodeVo();
        vo.setId(e.getId());
        vo.setParentId(e.getParentId());
        String displayName = resolveSubjectName(e);
        vo.setName(displayName);
        vo.setTitle(displayName);
        vo.setLevel(e.getLevel());
        vo.setSort(e.getSort());
        vo.setKnowledgeImg(e.getKnowledgeImg());
        vo.setKnowledgeVideo(e.getKnowledgeVideo());
        vo.setIsShare(e.getIsShare());
        vo.setCreateTime(e.getCreateTime() == null ? null : e.getCreateTime().getTime());
        vo.setKey(e.getId());
        vo.setValue(e.getId());
        vo.setNodeDataSum(null);
        return vo;
    }

    private void sortSubjectRecursive(List<SubjectNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        nodes.sort(Comparator.comparing(SubjectNodeVo::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
        for (SubjectNodeVo n : nodes) {
            sortSubjectRecursive(n.getChildren());
        }
    }

    private void markSubjectHasChildren(List<SubjectNodeVo> nodes) {
        if (nodes == null) {
            return;
        }
        for (SubjectNodeVo n : nodes) {
            if (n.getChildren() == null || n.getChildren().isEmpty()) {
                n.setHasChildren(false);
            }
            markSubjectHasChildren(n.getChildren());
        }
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
     * 本方法复制为纯 {@link FreeTagVo}（避免 Jackson 序列化时把 questionId 写进响应 JSON）。
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
        Map<Long, List<FreeTagVo>> grouped = new HashMap<>();
        for (BizQuestionFreeTagMapper.FreeTagWithQid row : all) {
            FreeTagVo pure = new FreeTagVo();
            pure.setId(row.getId());
            pure.setName(row.getName());
            pure.setPosition(row.getPosition());
            grouped.computeIfAbsent(row.getQuestionId(), k -> new ArrayList<>()).add(pure);
        }
        return grouped;
    }


    // ────────────────────────────────────────────────────────────
    // V-4 fileUpload — H1 卡段② BE 波 2c
    // ────────────────────────────────────────────────────────────

    /**
     * admin 图上传 — multipart → OSS + 写 image_asset。
     *
     * <p>实现要点：
     * <ol>
     *   <li>校验：file 非空 + size ≤ 5MB + 后缀白名单 + type 白名单兜底</li>
     *   <li>OSS key 手工构造 {@code admin-upload/<YYYY-MM-dd>/<uuid>.<ext>}（绕过
     *       {@link OssClient#uploadSuffix} 默认 properties.getPrefix() 拼接逻辑）</li>
     *   <li>调 {@link OssClient#upload(java.io.InputStream, String, Long, String)} 上传</li>
     *   <li>写 image_asset（src_url 用 admin-upload:// 虚 URL 兜底 NOT NULL；
     *       url_hash = SHA-256(srcUrl) 64 字符 hex；
     *       entity_ref='admin' 标识来源）</li>
     *   <li>返回 {@code {url: ossUrl, assetId: <自增 id>}}</li>
     * </ol>
     */
    @Override
    public Map<String, Object> adminUploadFile(MultipartFile file, String type) {
        // 题库语义层校验：H 卡白名单仅 stem/answer/explain（assetKind 业务边界 — 通用 service 不做）
        String assetKind = sanitizeUploadType(type);

        // 委托 admin-common 通用上传 service（H1 卡补丁抽离 — A/B 卡共用同一 service）
        return adminFileUploadService.uploadImage(
            file,
            ADMIN_UPLOAD_ENTITY_TYPE,   // "question"
            assetKind,                  // stem / answer / explain
            ADMIN_UPLOAD_ENTITY_REF,    // "admin"
            ADMIN_UPLOAD_KEY_PREFIX     // "admin-upload"
        );
    }

    /**
     * type 参数白名单 + null 兜底。null / 非法 → 默认 "stem"（H 卡题库语义专属）。
     */
    private String sanitizeUploadType(String type) {
        if (type == null) {
            return "stem";
        }
        String lower = type.trim().toLowerCase(Locale.ROOT);
        return ADMIN_UPLOAD_ALLOWED_TYPES.contains(lower) ? lower : "stem";
    }


    /**
     * admin freeTag 字典搜索（H1 卡 Bug2 补丁）。
     *
     * <p>直接走 {@link AdminFreeTagWriteMapper#selectListByKeyword(String, int)}，
     * 业务规则：
     * <ul>
     *   <li>keyword trim 后传入；为 null / "" → mapper 内 {@code <if>} 跳过 LIKE 过滤，返热门</li>
     *   <li>limit 兜底：null / ≤ 0 → 20；&gt; 100 → clamp 100</li>
     * </ul>
     *
     * @param keyword 关键字（null / 空 / 纯空格 → trim 后等效空 → 返热门）
     * @param limit   返回上限（兜底 20 / clamp 100）
     * @return tag 候选列表
     */
    @Override
    public List<Map<String, Object>> adminFreeTagSearch(String keyword, Integer limit) {
        String kw = keyword == null ? null : keyword.trim();
        int lim;
        if (limit == null || limit <= 0) {
            lim = 20;
        } else if (limit > 100) {
            lim = 100;
        } else {
            lim = limit;
        }
        return adminFreeTagWriteMapper.selectListByKeyword(kw, lim);
    }
}
