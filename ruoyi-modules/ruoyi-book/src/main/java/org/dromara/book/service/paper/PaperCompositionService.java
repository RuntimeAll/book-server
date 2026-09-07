package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.CreateExamPaperBo;
import org.dromara.book.domain.bo.PaperQuestionInputBo;
import org.dromara.book.domain.bo.UpdateExamPaperBo;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.entity.BizPaperCreateRequest;
import org.dromara.book.domain.entity.BizPaperQuestion;
import org.dromara.book.domain.entity.BizPaperSection;
import org.dromara.book.domain.entity.BizQuestionBasketEntry;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.domain.vo.CreateExamPaperVo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.mapper.BizPaperSectionMapper;
import org.dromara.book.mapper.PaperCreateRequestMapper;
import org.dromara.book.mapper.QuestionBasketEntryMapper;
import org.dromara.book.service.IPaperDetailService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaperCompositionService {
    private final BizPaperMapper paperMapper;
    private final BizPaperSectionMapper sectionMapper;
    private final BizPaperQuestionMapper paperQuestionMapper;
    private final BizPaperCategoryMapper categoryMapper;
    private final PaperCreateRequestMapper requestMapper;
    private final QuestionBasketEntryMapper basketMapper;
    private final QuestionSelectionResolver selectionResolver;
    private final QuestionSnapshotCodec snapshotCodec;
    private final IPaperDetailService detailService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public CreateExamPaperVo create(CreateExamPaperBo bo, Long ownerId) {
        if (ownerId == null) {
            throw new ServiceException("归属用户不能为空", 401);
        }
        SelectionRules.validateTitle(bo.getName());
        SelectionRules.validateTime(bo.getSuggestTime());
        SelectionRules.validateRequestId(bo.getRequestId(), bo.getQuestions() != null);
        List<PaperQuestionInputBo> rows = createRows(bo);
        SelectionRules.validateRows(rows);
        String requestId = bo.getRequestId() == null ? null : bo.getRequestId().toLowerCase(Locale.ROOT);
        return TenantHelper.ignore(() -> {
            String category = category(bo.getPaperCategoryId());
            int minutes = bo.getSuggestTime() == null ? SelectionRules.DEFAULT_SUGGEST_TIME : bo.getSuggestTime();
            if (requestId != null) {
                String hash = payloadHash(new CreateCommand(bo.getName(), category, minutes, rows));
                requestMapper.ensureRequest(ownerId, requestId, hash);
                BizPaperCreateRequest request = requestMapper.lockRequest(ownerId, requestId);
                if (!hash.equals(request.getPayloadHash())) {
                    throw new ServiceException("requestId已用于不同的组卷内容", 409);
                }
                if (request.getPaperId() != null) {
                    BizPaper saved = paperMapper.lockById(request.getPaperId());
                    if (saved == null) {
                        throw new ServiceException("本次创建的试卷已删除，请发起新的创建", 410);
                    }
                    return new CreateExamPaperVo(request.getPaperId(), request.getQuestionCount());
                }
            }
            Map<String, BasketEntryVo> selections = resolveNewRows(rows, ownerId);
            Date now = new Date();
            BizPaper paper = new BizPaper();
            paper.setName(bo.getName());
            paper.setSubjectId(category);
            paper.setPaperCategoryId(category);
            paper.setQuestionCount(rows.size());
            paper.setScore(totalScore(rows));
            paper.setSuggestTime(minutes);
            paper.setPaperType(1);
            paper.setPaperKind("1");
            paper.setStatus(BizPaper.STATUS_PUBLISHED);
            paper.setSort(0);
            paper.setCreateBy(ownerId.toString());
            paper.setUpdateBy(ownerId.toString());
            paper.setCreateTime(now);
            paper.setUpdateTime(now);
            if (paperMapper.insert(paper) != 1) {
                throw new ServiceException("试卷写入失败");
            }
            BizPaperSection section = new BizPaperSection();
            section.setPaperId(paper.getId());
            section.setTitle("题目");
            section.setSort(1);
            sectionMapper.insert(section);
            List<BizPaperQuestion> records = new ArrayList<>();
            for (PaperQuestionInputBo row : rows) {
                BizPaperQuestion record = questionRecord(paper.getId(), section.getId(), row,
                    selections.get(SelectionRules.entryKey(row)));
                records.add(record);
            }
            if (!paperQuestionMapper.insertBatch(records)) {
                throw new ServiceException("试卷题目写入失败");
            }
            if (requestId != null && requestMapper.complete(ownerId, requestId, paper.getId(), rows.size()) != 1) {
                throw new ServiceException("试卷创建状态写入失败");
            }
            return new CreateExamPaperVo(paper.getId(), rows.size());
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public PaperDetailVo update(UpdateExamPaperBo bo, Long userId) {
        if (userId == null || bo.getPaperId() == null) {
            throw new ServiceException("请先登录并指定试卷", 400);
        }
        if (bo.getName() != null) {
            SelectionRules.validateTitle(bo.getName());
        }
        SelectionRules.validateTime(bo.getSuggestTime());
        if (bo.getQuestions() == null || bo.getQuestions().isEmpty()
            || bo.getQuestions().size() > SelectionRules.MAX_PAPER_QUESTIONS) {
            throw new ServiceException("试卷题数需1-500题", 400);
        }
        return TenantHelper.ignore(() -> {
            BizPaper paper = paperMapper.lockById(bo.getPaperId());
            requireOwner(paper, userId);
            String category = bo.getPaperCategoryId() == null ? null : category(bo.getPaperCategoryId());
            List<BizPaperSection> sections = sectionMapper.selectList(new LambdaQueryWrapper<BizPaperSection>()
                .eq(BizPaperSection::getPaperId, paper.getId()).orderByAsc(BizPaperSection::getSort)
                .orderByAsc(BizPaperSection::getId));
            Set<Long> sectionIds = new HashSet<>();
            sections.forEach(section -> sectionIds.add(section.getId()));
            if (sections.isEmpty()) {
                throw new ServiceException("试卷缺少大题分组", 400);
            }
            validateSections(bo.getSections(), sections);
            Map<Long, BizPaperQuestion> previous = new HashMap<>();
            for (BizPaperQuestion record : paperQuestionMapper.selectList(new LambdaQueryWrapper<BizPaperQuestion>()
                .eq(BizPaperQuestion::getPaperId, paper.getId()))) {
                previous.put(record.getId(), record);
            }
            List<UpdateExamPaperBo.UpdateExamPaperQuestionBo> rows = new ArrayList<>();
            List<PaperQuestionInputBo> fresh = new ArrayList<>();
            Map<String, BasketEntryVo> resolved = new HashMap<>();
            Set<Long> retainedIds = new HashSet<>();
            for (UpdateExamPaperBo.UpdateExamPaperQuestionBo incoming : bo.getQuestions()) {
                if (incoming == null) {
                    throw new ServiceException("题目行不能为空", 400);
                }
                UpdateExamPaperBo.UpdateExamPaperQuestionBo row = new UpdateExamPaperBo.UpdateExamPaperQuestionBo();
                org.springframework.beans.BeanUtils.copyProperties(incoming, row);
                if (row.getSectionId() == null) {
                    row.setSectionId(sections.get(0).getId());
                }
                if (!sectionIds.contains(row.getSectionId())) {
                    throw new ServiceException("大题分组不属于当前试卷", 403);
                }
                if (row.getPaperQuestionId() != null) {
                    BizPaperQuestion old = previous.get(row.getPaperQuestionId());
                    if (old == null || !retainedIds.add(old.getId())
                        || !Objects.equals(old.getQuestionId(), row.getQuestionId())
                        || (row.getSourceBookId() != null && !Objects.equals(row.getSourceBookId(), old.getSourceBookId()))
                        || (row.getSourceItemId() != null && !Objects.equals(row.getSourceItemId(), old.getSourceItemId()))) {
                        throw new ServiceException("卷内题目不属于当前试卷或来源被修改", 403);
                    }
                    row.setSourceBookId(old.getSourceBookId());
                    row.setSourceItemId(old.getSourceItemId());
                    if (old.getSnapshotJson() != null) {
                        BasketEntryVo entry = new BasketEntryVo();
                        entry.setEntryKey(SelectionRules.entryKey(row));
                        entry.setQuestionId(old.getQuestionId());
                        entry.setSourceBookId(old.getSourceBookId());
                        entry.setSourceItemId(old.getSourceItemId());
                        entry.setQuestion(snapshotCodec.decode(old.getSnapshotJson()));
                        resolved.put(entry.getEntryKey(), entry);
                    } else {
                        row.setBasketNamespace(null);
                        fresh.add(row);
                    }
                } else {
                    fresh.add(row);
                }
                rows.add(row);
            }
            SelectionRules.validateRows(rows);
            resolved.putAll(resolveNewRows(fresh, userId));
            List<BizPaperQuestion> records = new ArrayList<>();
            for (UpdateExamPaperBo.UpdateExamPaperQuestionBo row : rows) {
                BizPaperQuestion record = questionRecord(paper.getId(), row.getSectionId(), row,
                    resolved.get(SelectionRules.entryKey(row)));
                record.setId(row.getPaperQuestionId());
                records.add(record);
            }
            // Everything, including source ownership and snapshots, is validated before replacing rows.
            paperQuestionMapper.delete(new LambdaQueryWrapper<BizPaperQuestion>()
                .eq(BizPaperQuestion::getPaperId, paper.getId()));
            if (!paperQuestionMapper.insertBatch(records)) {
                throw new ServiceException("试卷题目写入失败");
            }
            LambdaUpdateWrapper<BizPaper> update = new LambdaUpdateWrapper<BizPaper>()
                .eq(BizPaper::getId, paper.getId()).eq(BizPaper::getCreateBy, userId.toString())
                .set(BizPaper::getQuestionCount, rows.size()).set(BizPaper::getScore, totalScore(rows))
                .set(BizPaper::getUpdateBy, userId.toString()).set(BizPaper::getUpdateTime, new Date());
            if (bo.getName() != null) {
                update.set(BizPaper::getName, bo.getName());
            }
            if (bo.getSuggestTime() != null) {
                update.set(BizPaper::getSuggestTime, bo.getSuggestTime());
            }
            if (bo.getPaperCategoryId() != null) {
                update.set(BizPaper::getPaperCategoryId, category).set(BizPaper::getSubjectId, category);
            }
            if (paperMapper.update(null, update) != 1) {
                throw new ServiceException("试卷更新失败");
            }
            if (bo.getSections() != null) {
                // Release occupied unique (paper_id, sort) positions before a section-order swap.
                for (UpdateExamPaperBo.SectionBo change : bo.getSections()) {
                    if (change.getSort() != null) {
                        sectionMapper.update(null, new LambdaUpdateWrapper<BizPaperSection>()
                            .eq(BizPaperSection::getId, change.getSectionId())
                            .eq(BizPaperSection::getPaperId, paper.getId())
                            .set(BizPaperSection::getSort, -change.getSort()));
                    }
                }
                for (UpdateExamPaperBo.SectionBo change : bo.getSections()) {
                    LambdaUpdateWrapper<BizPaperSection> sectionUpdate = new LambdaUpdateWrapper<BizPaperSection>()
                        .eq(BizPaperSection::getId, change.getSectionId())
                        .eq(BizPaperSection::getPaperId, paper.getId());
                    if (change.getName() != null) {
                        sectionUpdate.set(BizPaperSection::getTitle, change.getName());
                    }
                    if (change.getSort() != null) {
                        sectionUpdate.set(BizPaperSection::getSort, change.getSort());
                    }
                    if ((change.getName() != null || change.getSort() != null)
                        && sectionMapper.update(null, sectionUpdate) != 1) {
                        throw new ServiceException("大题分组更新失败");
                    }
                }
            }
            return detailService.getPaperDetail(paper.getId());
        });
    }

    private Map<String, BasketEntryVo> resolveNewRows(List<? extends PaperQuestionInputBo> rows, Long userId) {
        Map<String, BasketEntryVo> result = new HashMap<>();
        List<PaperQuestionInputBo> direct = new ArrayList<>();
        Map<String, List<PaperQuestionInputBo>> fromBasket = new java.util.TreeMap<>();
        for (PaperQuestionInputBo row : rows) {
            if (row.getBasketEntryId() != null
                && (row.getBasketEntryId() <= 0 || row.getBasketNamespace() == null)) {
                throw new ServiceException("试题栏记录版本需要有效ID与命名空间", 400);
            }
            if (row.getBasketNamespace() == null) {
                direct.add(row);
            } else {
                String namespace = SelectionRules.namespace(row.getBasketNamespace());
                fromBasket.computeIfAbsent(namespace, unused -> new ArrayList<>()).add(row);
            }
        }
        if (!direct.isEmpty()) {
            for (BasketEntryVo entry : selectionResolver.resolve(direct, userId, LoginHelper.isSuperAdmin())) {
                result.put(entry.getEntryKey(), entry);
            }
        }
        for (Map.Entry<String, List<PaperQuestionInputBo>> scope : fromBasket.entrySet()) {
            selectionResolver.validate(scope.getValue(), userId, LoginHelper.isSuperAdmin());
            basketMapper.ensureScope(userId, scope.getKey());
            basketMapper.lockScope(userId, scope.getKey());
            List<String> keys = scope.getValue().stream().map(SelectionRules::entryKey).toList();
            List<BizQuestionBasketEntry> records = basketMapper.selectList(new LambdaQueryWrapper<BizQuestionBasketEntry>()
                .eq(BizQuestionBasketEntry::getUserId, userId)
                .eq(BizQuestionBasketEntry::getNamespace, scope.getKey())
                .in(BizQuestionBasketEntry::getEntryKey, keys).last("FOR UPDATE"));
            Map<String, BizQuestionBasketEntry> byKey = new HashMap<>();
            records.forEach(record -> byKey.put(record.getEntryKey(), record));
            for (PaperQuestionInputBo row : scope.getValue()) {
                BizQuestionBasketEntry record = byKey.get(SelectionRules.entryKey(row));
                if (record == null || !Objects.equals(record.getQuestionId(), row.getQuestionId())
                    || (row.getBasketEntryId() != null && !Objects.equals(record.getId(), row.getBasketEntryId()))
                    || !Objects.equals(record.getSourceBookId(), row.getSourceBookId())
                    || !Objects.equals(record.getSourceItemId(), row.getSourceItemId())) {
                    throw new ServiceException("试题栏条目缺失或来源不一致，请重新确认", 409);
                }
                BasketEntryVo entry = new BasketEntryVo();
                entry.setEntryKey(record.getEntryKey());
                entry.setQuestionId(record.getQuestionId());
                entry.setSourceBookId(record.getSourceBookId());
                entry.setSourceItemId(record.getSourceItemId());
                entry.setQuestion(snapshotCodec.decode(record.getSnapshotJson()));
                result.put(entry.getEntryKey(), entry);
            }
        }
        return result;
    }

    private List<PaperQuestionInputBo> createRows(CreateExamPaperBo bo) {
        if (bo.getQuestions() != null) {
            if (bo.getQuestionIds() != null) {
                throw new ServiceException("questions和questionIds不能同时提供", 400);
            }
            return bo.getQuestions();
        }
        if (bo.getQuestionIds() == null || bo.getQuestionIds().isEmpty()
            || bo.getQuestionIds().size() > SelectionRules.MAX_PAPER_QUESTIONS) {
            throw new ServiceException("试卷题数需1-500题", 400);
        }
        List<PaperQuestionInputBo> rows = new ArrayList<>();
        for (int i = 0; i < bo.getQuestionIds().size(); i++) {
            PaperQuestionInputBo row = new PaperQuestionInputBo();
            row.setQuestionId(bo.getQuestionIds().get(i));
            row.setSort(i + 1);
            row.setScore(BigDecimal.ZERO);
            rows.add(row);
        }
        return rows;
    }

    private BizPaperQuestion questionRecord(Long paperId, Long sectionId, PaperQuestionInputBo row,
                                            BasketEntryVo entry) {
        BizPaperQuestion record = new BizPaperQuestion();
        record.setPaperId(paperId);
        record.setSectionId(sectionId);
        record.setQuestionId(row.getQuestionId());
        record.setSourceBookId(row.getSourceBookId());
        record.setSourceItemId(row.getSourceItemId());
        record.setSort(row.getSort());
        record.setScore(row.getScore() == null ? BigDecimal.ZERO : row.getScore());
        record.setSnapshotJson(snapshotCodec.encode(entry.getQuestion()));
        return record;
    }

    private BigDecimal totalScore(List<? extends PaperQuestionInputBo> rows) {
        return rows.stream().map(PaperQuestionInputBo::getScore).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String category(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.matches("[0-9]{1,20}") || categoryMapper.selectById(value) == null) {
            throw new ServiceException("试卷分类不存在", 400);
        }
        return value;
    }

    private void requireOwner(BizPaper paper, Long userId) {
        if (paper == null || (!BizPaper.STATUS_DRAFT.equals(paper.getStatus())
            && !BizPaper.STATUS_PUBLISHED.equals(paper.getStatus()))) {
            throw new ServiceException("试卷不存在", 404);
        }
        if (!SelectionRules.canManagePaper(paper.getCreateBy(), userId)) {
            throw new ServiceException("无权编辑非本人创建的试卷", 403);
        }
    }

    private void validateSections(List<UpdateExamPaperBo.SectionBo> changes, List<BizPaperSection> current) {
        if (changes == null) {
            return;
        }
        Map<Long, BizPaperSection> byId = new HashMap<>();
        current.forEach(section -> byId.put(section.getId(), section));
        Set<Long> seen = new HashSet<>();
        for (UpdateExamPaperBo.SectionBo change : changes) {
            if (change == null || change.getSectionId() == null || !byId.containsKey(change.getSectionId())
                || !seen.add(change.getSectionId())) {
                throw new ServiceException("大题分组不属于当前试卷或重复", 403);
            }
            if (change.getName() != null && (change.getName().isBlank() || change.getName().length() > 50)) {
                throw new ServiceException("大题名称无效", 400);
            }
            if (change.getSort() != null && (change.getSort() < 1 || change.getSort() > 500)) {
                throw new ServiceException("大题排序需1-500", 400);
            }
            if (change.getSort() != null) {
                byId.get(change.getSectionId()).setSort(change.getSort());
            }
        }
        Set<Integer> sorts = new HashSet<>();
        for (BizPaperSection section : byId.values()) {
            if (!sorts.add(section.getSort())) {
                throw new ServiceException("大题排序不能重复", 400);
            }
        }
    }

    private String payloadHash(CreateCommand command) {
        try {
            byte[] payload = objectMapper.writeValueAsString(command).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash paper request", e);
        }
    }

    private record CreateCommand(String name, String category, int suggestTime, List<PaperQuestionInputBo> questions) {
    }
}
