package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionBasketBo;
import org.dromara.book.domain.entity.BizQuestionBasketEntry;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.domain.vo.BasketKeysVo;
import org.dromara.book.domain.vo.BasketPageVo;
import org.dromara.book.mapper.QuestionBasketEntryMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionBasketEntryService {
    private final QuestionBasketEntryMapper entryMapper;
    private final QuestionSelectionResolver selectionResolver;
    private final QuestionSnapshotCodec snapshotCodec;

    @Transactional(readOnly = true)
    public BasketKeysVo keys(String namespace) {
        Long userId = currentUser();
        String scope = SelectionRules.namespace(namespace);
        return TenantHelper.ignore(() -> new BasketKeysVo(
            entryMapper.selectEntryKeys(userId, scope, SelectionRules.MAX_PAPER_QUESTIONS)));
    }

    @Transactional(readOnly = true)
    public BasketPageVo page(String namespace, int pageIndex, int pageSize) {
        Long userId = currentUser();
        String scope = SelectionRules.namespace(namespace);
        if (pageIndex < 1 || pageSize < 1 || pageSize > SelectionRules.MAX_BATCH_SIZE) {
            throw new ServiceException("分页参数无效，每页最多100条", 400);
        }
        return TenantHelper.ignore(() -> {
            Page<BizQuestionBasketEntry> page = entryMapper.selectPage(new Page<>(pageIndex, pageSize),
                scope(userId, scope).orderByAsc(BizQuestionBasketEntry::getId));
            List<BasketEntryVo> entries = page.getRecords().stream().map(this::toVo).toList();
            return new BasketPageVo(entries, page.getTotal(), pageIndex, pageSize);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public int add(QuestionBasketBo bo) {
        Long userId = currentUser();
        String namespace = SelectionRules.namespace(bo.getNamespace());
        if (bo.getItems() == null || bo.getItems().isEmpty()
            || bo.getItems().size() > SelectionRules.MAX_BATCH_SIZE) {
            throw new ServiceException("每次添加需1-100条", 400);
        }
        return TenantHelper.ignore(() -> {
            lock(userId, namespace);
            List<BasketEntryVo> resolved = selectionResolver.resolve(bo.getItems(), userId, LoginHelper.isSuperAdmin());
            List<BizQuestionBasketEntry> existing = entryMapper.selectList(scope(userId, namespace)
                .select(BizQuestionBasketEntry::getEntryKey));
            Set<String> keys = new HashSet<>();
            existing.forEach(entry -> keys.add(entry.getEntryKey()));
            Map<String, BasketEntryVo> additions = new LinkedHashMap<>();
            for (BasketEntryVo entry : resolved) {
                if (!keys.contains(entry.getEntryKey())) {
                    additions.putIfAbsent(entry.getEntryKey(), entry);
                }
            }
            if (keys.size() + additions.size() > SelectionRules.MAX_PAPER_QUESTIONS) {
                throw new ServiceException("每个试题栏最多500条", 400);
            }
            for (BasketEntryVo entry : additions.values()) {
                BizQuestionBasketEntry record = new BizQuestionBasketEntry();
                record.setUserId(userId);
                record.setNamespace(namespace);
                record.setEntryKey(entry.getEntryKey());
                record.setQuestionId(entry.getQuestionId());
                record.setSourceBookId(entry.getSourceBookId());
                record.setSourceItemId(entry.getSourceItemId());
                record.setSnapshotJson(snapshotCodec.encode(entry.getQuestion()));
                if (entryMapper.insert(record) != 1) {
                    throw new ServiceException("试题栏写入失败");
                }
            }
            return additions.size();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(QuestionBasketBo bo) {
        Long userId = currentUser();
        String namespace = SelectionRules.namespace(bo.getNamespace());
        if (bo.getEntryKeys() == null || bo.getEntryKeys().isEmpty()
            || bo.getEntryKeys().size() > SelectionRules.MAX_BATCH_SIZE
            || bo.getEntryKeys().stream().anyMatch(key -> key == null || !key.matches("(q|shelf):[1-9][0-9]{0,18}"))) {
            throw new ServiceException("删除实例键无效，每次最多100条", 400);
        }
        TenantHelper.ignore(() -> {
            lock(userId, namespace);
            entryMapper.delete(scope(userId, namespace).in(BizQuestionBasketEntry::getEntryKey, bo.getEntryKeys()));
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeEntries(QuestionBasketBo bo) {
        Long userId = currentUser();
        String namespace = SelectionRules.namespace(bo.getNamespace());
        if (bo.getEntries() == null || bo.getEntries().isEmpty()
            || bo.getEntries().size() > SelectionRules.MAX_BATCH_SIZE
            || bo.getEntries().stream().anyMatch(entry -> entry == null || entry.getBasketEntryId() == null
                || entry.getBasketEntryId() <= 0 || entry.getEntryKey() == null
                || !entry.getEntryKey().matches("(q|shelf):[1-9][0-9]{0,18}"))) {
            throw new ServiceException("删除记录版本无效，每次最多100条", 400);
        }
        TenantHelper.ignore(() -> {
            lock(userId, namespace);
            entryMapper.deleteVersions(userId, namespace, bo.getEntries());
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void empty(QuestionBasketBo bo) {
        Long userId = currentUser();
        String namespace = SelectionRules.namespace(bo.getNamespace());
        TenantHelper.ignore(() -> {
            lock(userId, namespace);
            entryMapper.delete(scope(userId, namespace));
        });
    }

    private void lock(Long userId, String namespace) {
        entryMapper.ensureScope(userId, namespace);
        entryMapper.lockScope(userId, namespace);
    }

    private BasketEntryVo toVo(BizQuestionBasketEntry record) {
        BasketEntryVo entry = new BasketEntryVo();
        entry.setBasketEntryId(record.getId());
        entry.setEntryKey(record.getEntryKey());
        entry.setQuestionId(record.getQuestionId());
        entry.setSourceBookId(record.getSourceBookId());
        entry.setSourceItemId(record.getSourceItemId());
        entry.setQuestion(snapshotCodec.decode(record.getSnapshotJson()));
        return entry;
    }

    private LambdaQueryWrapper<BizQuestionBasketEntry> scope(Long userId, String namespace) {
        return new LambdaQueryWrapper<BizQuestionBasketEntry>()
            .eq(BizQuestionBasketEntry::getUserId, userId)
            .eq(BizQuestionBasketEntry::getNamespace, namespace);
    }

    private Long currentUser() {
        Long id = LoginHelper.getUserId();
        if (id == null) {
            throw new ServiceException("请先登录", 401);
        }
        return id;
    }
}
