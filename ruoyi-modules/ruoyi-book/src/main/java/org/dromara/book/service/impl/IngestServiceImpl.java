package org.dromara.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.bo.IngestAiBo;
import org.dromara.book.domain.bo.IngestBookBo;
import org.dromara.book.domain.bo.IngestKgContentBo;
import org.dromara.book.domain.bo.IngestKgTreeBo;
import org.dromara.book.domain.bo.IngestQuestionBo;
import org.dromara.book.domain.entity.BizBook;
import org.dromara.book.domain.entity.BizBookQuestion;
import org.dromara.book.domain.entity.BizBookSubject;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizQuestionAi;
import org.dromara.book.domain.entity.BizQuestionBlock;
import org.dromara.book.domain.entity.BizQuestionImage;
import org.dromara.book.domain.entity.BizQuestionKnowledge;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.book.domain.entity.BizSubjectContent;
import org.dromara.book.domain.entity.BizTextContent;
import org.dromara.book.domain.entity.ImageAsset;
import org.dromara.book.mapper.BizBookMapper;
import org.dromara.book.mapper.BizBookQuestionMapper;
import org.dromara.book.mapper.BizBookSubjectMapper;
import org.dromara.book.mapper.BizQuestionAiMapper;
import org.dromara.book.mapper.BizQuestionBlockMapper;
import org.dromara.book.mapper.BizQuestionImageMapper;
import org.dromara.book.mapper.BizQuestionKnowledgeMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizSubjectContentMapper;
import org.dromara.book.mapper.BizSubjectMapper;
import org.dromara.book.mapper.BizTextContentMapper;
import org.dromara.book.mapper.ImageAssetMapper;
import org.dromara.book.service.IIngestService;
import org.dromara.book.util.BlockJsonValidator;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 录入接口 Service 实现（PRD-C-204 B1）。
 *
 * <p>🔴 全程 {@code TenantHelper.ignore(DataPermissionHelper.ignore(...))} 包裹 —— biz_* 表无
 * tenant_id 且老题 create_user≠登录 id，不 ignore 会被数据权限拦截器注入 AND create_by 静默 0 行
 * （同 QuestionServiceImpl.create 致命实战）。JSON 字段以 String 承载，service 内 JsonUtils 序列化。
 *
 * <p>幂等：upsert（biz_subject/biz_book 等用 INSERT ... ON DUPLICATE KEY UPDATE 语义，
 * 这里走 selectById 判存在 → insert/updateById 等价实现）；录题用 externalKey→md5 存 stem_hash 去重。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestServiceImpl implements IIngestService {

    private final BizSubjectMapper bizSubjectMapper;
    private final BizSubjectContentMapper bizSubjectContentMapper;
    private final ImageAssetMapper imageAssetMapper;
    private final BizBookMapper bizBookMapper;
    private final BizBookSubjectMapper bizBookSubjectMapper;
    private final BizBookQuestionMapper bizBookQuestionMapper;
    private final BizQuestionMapper bizQuestionMapper;
    private final BizTextContentMapper bizTextContentMapper;
    private final BizQuestionKnowledgeMapper bizQuestionKnowledgeMapper;
    private final BizQuestionImageMapper bizQuestionImageMapper;
    private final BizQuestionAiMapper bizQuestionAiMapper;
    private final BizQuestionBlockMapper bizQuestionBlockMapper;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /** 允许图片 MIME → 兜底后缀 */
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp",
        "image/gif", ".gif"
    );

    /** 后缀 → MIME（localPath 直传时按后缀推 contentType） */
    private static final Map<String, String> EXT_TO_MIME = Map.of(
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "webp", "image/webp",
        "gif", "image/gif"
    );

    // ==================== 组1 KG ====================

    @Override
    public Map<String, Object> upsertKgTree(IngestKgTreeBo bo) {
        if (bo == null || bo.getNodes() == null || bo.getNodes().isEmpty()) {
            throw new ServiceException("nodes 不能为空");
        }
        int n = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            int upserted = 0;
            for (IngestKgTreeBo.Node node : bo.getNodes()) {
                if (StringUtils.isBlank(node.getId())) {
                    continue;
                }
                BizSubject existing = bizSubjectMapper.selectById(node.getId());
                BizSubject row = new BizSubject();
                row.setId(node.getId());
                row.setParentId(node.getParentId());
                row.setName(node.getName());
                row.setLevel(node.getLevel());
                row.setSort(node.getSort());
                if (existing == null) {
                    row.setStatus("0");
                    bizSubjectMapper.insert(row);
                } else {
                    bizSubjectMapper.updateById(row);
                }
                upserted++;
            }
            return upserted;
        }));
        Map<String, Object> r = new HashMap<>();
        r.put("upserted", n);
        return r;
    }

    @Override
    public Map<String, Object> upsertKgContent(IngestKgContentBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getSubjectId())) {
            throw new ServiceException("subjectId 不能为空");
        }
        List<IngestKgContentBo.Block> blocks = bo.getBlocks() == null ? List.of() : bo.getBlocks();
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            // 幂等：先按 subject_id 清旧内容块再整段重写（重跑等价）
            bizSubjectContentMapper.delete(new LambdaQueryWrapper<BizSubjectContent>()
                .eq(BizSubjectContent::getSubjectId, bo.getSubjectId()));
            int sort = 0;
            for (IngestKgContentBo.Block b : blocks) {
                BizSubjectContent row = new BizSubjectContent();
                row.setSubjectId(bo.getSubjectId());
                row.setContentType(b.getKind());
                // 配图 imgUrl 一并序进 content（JSON），无图则存纯文本
                if (StringUtils.isNotBlank(b.getImgUrl())) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("text", b.getText());
                    payload.put("imgUrl", b.getImgUrl());
                    row.setContent(JsonUtils.toJsonString(payload));
                } else {
                    row.setContent(b.getText());
                }
                row.setSource("U");
                row.setSort(sort++);
                bizSubjectContentMapper.insert(row);
            }
            return null;
        }));
        Map<String, Object> r = new HashMap<>();
        r.put("subjectId", bo.getSubjectId());
        r.put("blocks", blocks.size());
        return r;
    }

    // ==================== 组2 图资产 ====================

    @Override
    public Map<String, Object> uploadImage(MultipartFile file, String assetKind) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.containsKey(contentType.toLowerCase())) {
            throw new ServiceException("仅支持 jpg/png/webp/gif 图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("图片不能超过 10MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败：" + e.getMessage());
        }
        String originalName = StringUtils.defaultString(file.getOriginalFilename());
        String suffix = suffixOf(originalName, contentType);
        String srcRef = originalName.isEmpty() ? "multipart:" + suffix : originalName;
        return upsertImageAsset(bytes, suffix, contentType.toLowerCase(), assetKind, srcRef);
    }

    @Override
    public Map<String, Object> uploadImageByLocalPath(String localPath, String assetKind) {
        if (StringUtils.isBlank(localPath)) {
            throw new ServiceException("localPath 不能为空");
        }
        Path path = Paths.get(localPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ServiceException("本地文件不存在：" + localPath);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ServiceException("读取本地文件失败：" + e.getMessage());
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new ServiceException("图片不能超过 10MB");
        }
        String fileName = path.getFileName().toString();
        String ext = fileName.contains(".")
            ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        String contentType = EXT_TO_MIME.get(ext);
        if (contentType == null) {
            throw new ServiceException("仅支持 jpg/png/webp/gif 图片");
        }
        String suffix = "." + ext;
        return upsertImageAsset(bytes, suffix, contentType, assetKind, localPath);
    }

    /** 上传 OSS + 去重 upsert image_asset（sha256 命中则不重传）。 */
    private Map<String, Object> upsertImageAsset(byte[] bytes, String suffix, String contentType,
                                                 String assetKind, String srcRef) {
        String hash = sha256Hex(bytes);
        String kind = StringUtils.isBlank(assetKind) ? "figure" : assetKind;
        return TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            ImageAsset existing = imageAssetMapper.selectOne(new LambdaQueryWrapper<ImageAsset>()
                .eq(ImageAsset::getUrlHash, hash).last("limit 1"));
            if (existing != null) {
                Map<String, Object> r = new HashMap<>();
                r.put("assetId", existing.getId());
                r.put("ossUrl", existing.getOssUrl());
                r.put("dedup", true);
                return r;
            }
            // 上传 OSS（默认公开读桶，与 SysOssServiceImpl.upload / VariantUpload 同范式）
            OssClient storage = OssFactory.instance();
            UploadResult uploadResult = storage.uploadSuffix(bytes, suffix, contentType);
            Date now = new Date();
            ImageAsset row = new ImageAsset();
            row.setSrcUrl(StringUtils.substring(srcRef, 0, 1024));
            row.setUrlHash(hash);
            row.setAssetKind(kind);
            row.setExt(suffix.startsWith(".") ? suffix.substring(1) : suffix);
            row.setRelPath(uploadResult.getFilename());
            row.setLocalPath(StringUtils.substring(srcRef, 0, 1024));
            row.setStatus("uploaded");
            row.setFileSize((long) bytes.length);
            row.setContentType(contentType);
            row.setOssUrl(uploadResult.getUrl());
            row.setOssUploadedTs(now);
            row.setCreatedAt(now);
            imageAssetMapper.insert(row);
            log.info("[ingest-image] assetId={} hash={} key={} url={}",
                row.getId(), hash, uploadResult.getFilename(), uploadResult.getUrl());
            Map<String, Object> r = new HashMap<>();
            r.put("assetId", row.getId());
            r.put("ossUrl", uploadResult.getUrl());
            r.put("dedup", false);
            return r;
        }));
    }

    // ==================== 组3 教辅 + 录题 ====================

    @Override
    public Map<String, Object> upsertBook(IngestBookBo bo) {
        if (bo == null || bo.getBook() == null || StringUtils.isBlank(bo.getBook().getId())) {
            throw new ServiceException("book.id 不能为空");
        }
        List<IngestBookBo.BookSubjectNode> nodes =
            bo.getBookSubject() == null ? List.of() : bo.getBookSubject();
        TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            IngestBookBo.Book b = bo.getBook();
            BizBook existing = bizBookMapper.selectById(b.getId());
            BizBook row = new BizBook();
            row.setId(b.getId());
            row.setFullName(b.getFullName());
            row.setEdition(b.getEdition());
            row.setGrade(b.getGrade());
            row.setTerm(b.getTerm());
            row.setSeries(b.getSeries());
            row.setBookType(b.getBookType());
            row.setPublisher(b.getPublisher());
            row.setSubjectName(b.getSubjectName());
            row.setCoverUrl(b.getCoverUrl());
            if (existing == null) {
                row.setStatus("0");
                row.setCreateTime(new Date());
                bizBookMapper.insert(row);
            } else {
                bizBookMapper.updateById(row);
            }
            for (IngestBookBo.BookSubjectNode node : nodes) {
                if (StringUtils.isBlank(node.getId())) {
                    continue;
                }
                BizBookSubject ns = bizBookSubjectMapper.selectById(node.getId());
                BizBookSubject sub = new BizBookSubject();
                sub.setId(node.getId());
                sub.setBookId(b.getId());
                sub.setParentId(node.getParentId());
                sub.setName(node.getName());
                sub.setLevel(node.getLevel());
                sub.setSort(node.getSort());
                if (ns == null) {
                    sub.setStatus("0");
                    sub.setCreateTime(new Date());
                    bizBookSubjectMapper.insert(sub);
                } else {
                    bizBookSubjectMapper.updateById(sub);
                }
            }
            return null;
        }));
        Map<String, Object> r = new HashMap<>();
        r.put("bookId", bo.getBook().getId());
        r.put("bookSubjectNodes", nodes.size());
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestQuestion(IngestQuestionBo bo, Long teacherId) {
        if (bo == null) {
            throw new ServiceException("录题入参不能为空");
        }
        if (bo.getQuestionType() == null) {
            throw new ServiceException("questionType 不能为空");
        }
        if (bo.getDifficult() == null) {
            throw new ServiceException("difficult 不能为空");
        }
        if (StringUtils.isBlank(bo.getSubjectId())) {
            throw new ServiceException("subjectId 不能为空");
        }
        final String ownerIdStr = teacherId == null ? null : String.valueOf(teacherId);
        // externalKey → md5 存 stem_hash 做去重判存在
        final String stemHash = StringUtils.isBlank(bo.getExternalKey())
            ? null : md5Hex(bo.getExternalKey());

        Map<String, Object> result = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            Date now = new Date();
            // 幂等：externalKey 命中已存在 → 更新该题（不新建）
            Long existingId = null;
            if (stemHash != null) {
                BizQuestion hit = bizQuestionMapper.selectOne(new LambdaQueryWrapper<BizQuestion>()
                    .eq(BizQuestion::getStemHash, stemHash).last("limit 1"));
                if (hit != null) {
                    existingId = hit.getId();
                }
            }
            boolean created = existingId == null;

            BizQuestion q = new BizQuestion();
            q.setId(existingId);
            q.setQuestionType(bo.getQuestionType());
            q.setDifficult(bo.getDifficult());
            q.setSubjectId(bo.getSubjectId());
            q.setIsAnchor(bo.getIsAnchor() != null ? bo.getIsAnchor() : 0);
            // dim1_kp_id = 主知识点（knowledgeIds 中 isPrimary=1 的首个）
            q.setDim1KpId(primaryKpId(bo));
            q.setDim2Qtype(bo.getQuestionType());
            q.setDim4Difficulty(bo.getDifficult());
            q.setStemText(bo.getStemText());
            q.setStemHash(stemHash);
            q.setExamYear(bo.getExamYear());
            q.setRegionCode(bo.getRegionCode());
            q.setSourceType(bo.getSourceType());
            q.setSourceRaw(bo.getSourceRaw());
            q.setMotherSource(bo.getMotherSource());
            q.setImportSource(StringUtils.isBlank(bo.getImportSource()) ? "main" : bo.getImportSource());
            q.setImportBatchId(bo.getImportBatchId());
            q.setVersion(1010);
            q.setStatus("1");
            if (created) {
                q.setCreateUser(teacherId);
                q.setCreateBy(ownerIdStr);
                q.setCreateTime(now);
            }
            q.setUpdateBy(ownerIdStr);
            q.setUpdateTime(now);

            if (created) {
                bizQuestionMapper.insert(q);
            } else {
                bizQuestionMapper.updateById(q);
            }
            final Long qid = q.getId();

            // 外置三要素长文本：answer→'A' / analyze→'E'（stem 走 stem_text 列，文本不强外置）。
            // 重跑：先清旧 A/E 再写。
            bizTextContentMapper.delete(new LambdaQueryWrapper<BizTextContent>()
                .eq(BizTextContent::getQuestionId, qid)
                .in(BizTextContent::getContentType, List.of("A", "E")));
            Long answerTcId = StringUtils.isBlank(bo.getAnswerText())
                ? null : insertTextContent(qid, "A", bo.getAnswerText());
            Long analyzeTcId = StringUtils.isBlank(bo.getAnalyzeText())
                ? null : insertTextContent(qid, "E", bo.getAnalyzeText());
            if (answerTcId != null || analyzeTcId != null) {
                BizQuestion fk = new BizQuestion();
                fk.setId(qid);
                fk.setAnswerTextContentId(answerTcId);
                fk.setAnalyzeTextContentId(analyzeTcId);
                bizQuestionMapper.updateById(fk);
            }

            // biz_question_knowledge（N 条，重跑先清）
            bizQuestionKnowledgeMapper.delete(new LambdaQueryWrapper<BizQuestionKnowledge>()
                .eq(BizQuestionKnowledge::getQuestionId, qid));
            if (bo.getKnowledgeIds() != null) {
                Set<String> seen = new HashSet<>();
                for (IngestQuestionBo.KnowledgeRef kr : bo.getKnowledgeIds()) {
                    if (kr == null || StringUtils.isBlank(kr.getKpId()) || !seen.add(kr.getKpId())) {
                        continue;
                    }
                    BizQuestionKnowledge k = new BizQuestionKnowledge();
                    k.setQuestionId(qid);
                    k.setKnowledgeId(kr.getKpId());
                    k.setSource(StringUtils.isBlank(kr.getSource()) ? "U" : kr.getSource());
                    k.setIsPrimary(kr.getIsPrimary() != null ? kr.getIsPrimary() : 0);
                    bizQuestionKnowledgeMapper.insert(k);
                }
            }

            // biz_question_image（N 条，重跑先清）
            bizQuestionImageMapper.delete(new LambdaQueryWrapper<BizQuestionImage>()
                .eq(BizQuestionImage::getQuestionId, qid));
            if (bo.getImages() != null) {
                for (IngestQuestionBo.ImageRef ir : bo.getImages()) {
                    if (ir == null || ir.getAssetId() == null) {
                        continue;
                    }
                    BizQuestionImage img = new BizQuestionImage();
                    img.setQuestionId(qid);
                    img.setAssetId(ir.getAssetId());
                    img.setOssUrl(ir.getOssUrl());
                    img.setBlockId(ir.getBlockId());
                    img.setRole(ir.getRole());
                    img.setSeq(ir.getSeq());
                    img.setIsDecorative(ir.getIsDecorative() != null ? ir.getIsDecorative() : 0);
                    img.setCreateTime(now);
                    bizQuestionImageMapper.insert(img);
                }
            }

            // biz_book_question（教辅编排，重跑先清本题该教辅的挂接）
            IngestQuestionBo.BookRef br = bo.getBook();
            if (br != null && StringUtils.isNotBlank(br.getBookId())) {
                bizBookQuestionMapper.delete(new LambdaQueryWrapper<BizBookQuestion>()
                    .eq(BizBookQuestion::getQuestionId, qid)
                    .eq(BizBookQuestion::getBookId, br.getBookId()));
                BizBookQuestion bq = new BizBookQuestion();
                bq.setBookId(br.getBookId());
                bq.setQuestionId(qid);
                bq.setContainerType(StringUtils.isBlank(br.getContainerType()) ? "section" : br.getContainerType());
                bq.setContainerId(br.getContainerId());
                bq.setColumnType(br.getColumnType());
                bq.setBookDifficulty(br.getBookDifficulty());
                bq.setRole(br.getRole());
                bq.setInBlockSeq(br.getInBlockSeq());
                bq.setCreateTime(now);
                bizBookQuestionMapper.insert(bq);
            }

            // biz_question_block（blockJson 非空校验后写，重跑覆盖；PK=questionId）
            if (StringUtils.isNotBlank(bo.getBlockJson())) {
                BlockJsonValidator.validate(bo.getBlockJson());
                BizQuestionBlock old = bizQuestionBlockMapper.selectById(qid);
                BizQuestionBlock block = new BizQuestionBlock();
                block.setQuestionId(qid);
                block.setBlockJson(bo.getBlockJson());
                block.setV(1);
                block.setUpdateBy(teacherId);
                block.setUpdateTime(now);
                if (old == null) {
                    block.setCreateTime(now);
                    bizQuestionBlockMapper.insert(block);
                } else {
                    bizQuestionBlockMapper.updateById(block);
                }
            }

            Map<String, Object> r = new HashMap<>();
            r.put("questionId", qid);
            r.put("created", created);
            return r;
        }));
        return result;
    }

    // ==================== 组4 AI 打标 ====================

    @Override
    public Map<String, Object> upsertAi(Long questionId, IngestAiBo bo, Long teacherId) {
        if (questionId == null) {
            throw new ServiceException("questionId 不能为空");
        }
        if (bo == null) {
            throw new ServiceException("打标入参不能为空");
        }
        final int annotateVersion = bo.getAnnotateVersion() != null ? bo.getAnnotateVersion() : 1;
        Long aiId = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> {
            Date now = new Date();
            BizQuestionAi existing = bizQuestionAiMapper.selectOne(new LambdaQueryWrapper<BizQuestionAi>()
                .eq(BizQuestionAi::getQuestionId, questionId)
                .eq(BizQuestionAi::getAnnotateVersion, annotateVersion)
                .last("limit 1"));
            BizQuestionAi ai = new BizQuestionAi();
            if (existing != null) {
                ai.setId(existing.getId());
            }
            ai.setQuestionId(questionId);
            ai.setAnnotateVersion(annotateVersion);
            ai.setSolutionSkeleton(bo.getSolutionSkeleton());
            ai.setAssessmentType(bo.getAssessmentType());
            int hpCount = bo.getHardPoints() == null ? 0 : bo.getHardPoints().size();
            ai.setHardPointCount(hpCount);
            ai.setHardPoints(toJsonArray(bo.getHardPoints()));
            ai.setBreakthroughPoints(toJsonArray(bo.getBreakthroughPoints()));
            ai.setTags(toJsonArray(bo.getTags()));
            ai.setParametricSlots(toJsonArray(bo.getParametricSlots()));
            ai.setModelingFrame(toJson(bo.getModelingFrame()));
            ai.setConditions(toJson(bo.getConditions()));
            ai.setVariationProfile(toJson(bo.getVariationProfile()));
            ai.setScenario(bo.getScenario());
            ai.setVerifyKind(bo.getVerifyKind());
            ai.setDnaType(bo.getDnaType());
            ai.setAnchorId(bo.getAnchorKpId());
            ai.setConfidence(bo.getAnchorConfidence());
            ai.setNeedAnchorReview(bo.getNeedAnchorReview() != null ? bo.getNeedAnchorReview() : 0);
            ai.setLabelStatus(bo.getLabelStatus() != null ? bo.getLabelStatus() : 1);
            ai.setLabeledBy(StringUtils.isBlank(bo.getLabeledBy())
                ? (teacherId == null ? null : String.valueOf(teacherId)) : bo.getLabeledBy());
            ai.setLabeledAt(now);
            if (existing == null) {
                ai.setCreateTime(now);
                bizQuestionAiMapper.insert(ai);
            } else {
                bizQuestionAiMapper.updateById(ai);
            }

            // 回写 biz_question 打标元（label_status/labeled_by/annotate_status=1）
            BizQuestion q = new BizQuestion();
            q.setId(questionId);
            q.setLabelStatus(bo.getLabelStatus() != null ? bo.getLabelStatus() : 1);
            q.setLabeledBy(ai.getLabeledBy());
            q.setLabeledAt(now);
            q.setLabelConfidence(bo.getAnchorConfidence());
            q.setAnnotateStatus(1);
            bizQuestionMapper.updateById(q);

            return ai.getId();
        }));
        Map<String, Object> r = new HashMap<>();
        r.put("aiId", aiId);
        return r;
    }

    // ==================== helpers ====================

    private Long insertTextContent(Long questionId, String contentType, String content) {
        BizTextContent tc = new BizTextContent();
        tc.setQuestionId(questionId);
        tc.setContentType(contentType);
        tc.setContent(content);
        bizTextContentMapper.insert(tc);
        return tc.getId();
    }

    /** knowledgeIds 中 isPrimary=1 的首个 kpId 作为 dim1_kp_id（无则取首个，再无则 null）。 */
    private String primaryKpId(IngestQuestionBo bo) {
        if (bo.getKnowledgeIds() == null || bo.getKnowledgeIds().isEmpty()) {
            return null;
        }
        String first = null;
        for (IngestQuestionBo.KnowledgeRef kr : bo.getKnowledgeIds()) {
            if (kr == null || StringUtils.isBlank(kr.getKpId())) {
                continue;
            }
            if (first == null) {
                first = kr.getKpId();
            }
            if (kr.getIsPrimary() != null && kr.getIsPrimary() == 1) {
                return kr.getKpId();
            }
        }
        return first;
    }

    private String suffixOf(String originalName, String contentType) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.'));
        }
        return ALLOWED_CONTENT_TYPES.get(contentType.toLowerCase());
    }

    private String toJsonArray(List<?> list) {
        if (list == null) {
            return null;
        }
        return JsonUtils.toJsonString(list);
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String s) {
            // 已是 JSON 串则原样；否则当作纯字符串包成 JSON 字符串
            String t = s.trim();
            return (t.startsWith("{") || t.startsWith("[")) ? s : JsonUtils.toJsonString(s);
        }
        return JsonUtils.toJsonString(obj);
    }

    private static String sha256Hex(byte[] data) {
        return digestHex("SHA-256", data);
    }

    private static String md5Hex(String s) {
        return digestHex("MD5", s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String digestHex(String algo, byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ServiceException("摘要计算失败：" + e.getMessage());
        }
    }
}
