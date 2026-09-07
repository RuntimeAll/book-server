package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.bo.QuestionBasketBo;
import org.dromara.book.domain.bo.SelectionReferenceBo;
import org.dromara.book.domain.entity.BizQuestionBasketEntry;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.QuestionBasketEntryMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class QuestionBasketEntryServiceTest {
    private final QuestionBasketEntryMapper entries = mock(QuestionBasketEntryMapper.class);
    private final QuestionSelectionResolver resolver = mock(QuestionSelectionResolver.class);
    private final QuestionSnapshotCodec codec = new QuestionSnapshotCodec(new ObjectMapper());
    private final QuestionBasketEntryService service = new QuestionBasketEntryService(entries, resolver, codec);

    @BeforeAll
    static void initializeMetadata() {
        PaperTestMetadata.initialize();
    }

    @Test
    void submittedRemovalRequiresRecordVersionAndKeepsScope() {
        QuestionBasketBo bo = request();
        QuestionBasketBo.EntryVersion entry = new QuestionBasketBo.EntryVersion();
        entry.setEntryKey("q:1");
        entry.setBasketEntryId(123L);
        bo.setEntries(List.of(entry));
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.removeEntries(bo);
            verify(entries).deleteVersions(9L, "lesson:123", bo.getEntries());
            entry.setBasketEntryId(null);
            assertEquals(400, assertThrows(ServiceException.class, () -> service.removeEntries(bo)).getCode());
        }
    }

    @Test
    void duplicateReferencesCountOnlyOnceAndPersistFrozenContent() {
        QuestionBasketBo bo = request();
        when(resolver.resolve(anyList(), eq(9L), eq(false))).thenReturn(List.of(resolved(), resolved()));
        when(entries.selectList(any())).thenReturn(List.of());
        when(entries.insert(any(BizQuestionBasketEntry.class))).thenReturn(1);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertEquals(1, service.add(bo));
        }
        ArgumentCaptor<BizQuestionBasketEntry> record = ArgumentCaptor.forClass(BizQuestionBasketEntry.class);
        verify(entries).insert(record.capture());
        assertEquals(9L, record.getValue().getUserId());
        assertEquals("lesson:123", record.getValue().getNamespace());
        assertEquals("frozen", codec.decode(record.getValue().getSnapshotJson()).getStemText());
        var ordered = inOrder(entries);
        ordered.verify(entries).ensureScope(9L, "lesson:123");
        ordered.verify(entries).lockScope(9L, "lesson:123");
        ordered.verify(entries).selectList(any());
        ordered.verify(entries).insert(any(BizQuestionBasketEntry.class));
    }

    @Test
    void basketCapacityFailsBeforeInsert() {
        when(resolver.resolve(anyList(), eq(9L), eq(false))).thenReturn(List.of(resolved()));
        List<BizQuestionBasketEntry> full = IntStream.range(2, 502).mapToObj(id -> {
            BizQuestionBasketEntry entry = new BizQuestionBasketEntry();
            entry.setEntryKey("q:" + id);
            return entry;
        }).toList();
        when(entries.selectList(any())).thenReturn(full);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertThrows(ServiceException.class, () -> service.add(request()));
        }
        verify(entries, never()).insert(any(BizQuestionBasketEntry.class));
    }

    @Test
    void writeFailurePropagatesInsteadOfSuccessfulAcknowledgement() {
        when(resolver.resolve(anyList(), eq(9L), eq(false))).thenReturn(List.of(resolved()));
        when(entries.selectList(any())).thenReturn(List.of());
        when(entries.insert(any(BizQuestionBasketEntry.class))).thenThrow(new IllegalStateException("database unavailable"));
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertThrows(IllegalStateException.class, () -> service.add(request()));
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void readsFrozenSnapshotAndEmptyIsScopedToCurrentUserAndNamespace() {
        BizQuestionBasketEntry row = new BizQuestionBasketEntry();
        row.setEntryKey("q:1");
        row.setQuestionId(1L);
        row.setSnapshotJson(codec.encode(resolved().getQuestion()));
        when(entries.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<BizQuestionBasketEntry> page = invocation.getArgument(0);
            return page.setRecords(List.of(row)).setTotal(1);
        });
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            var page = service.page("lesson:123", 1, 50);
            assertEquals(1, page.total());
            assertEquals("frozen", page.list().get(0).getQuestion().getStemText());
            service.empty(request());
        }
        ArgumentCaptor<Wrapper<BizQuestionBasketEntry>> where = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(entries).delete(where.capture());
        var wrapper = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizQuestionBasketEntry>)
            where.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("namespace"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(9L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("lesson:123"));
    }

    private QuestionBasketBo request() {
        QuestionBasketBo bo = new QuestionBasketBo();
        bo.setNamespace("lesson:123");
        SelectionReferenceBo reference = new SelectionReferenceBo();
        reference.setQuestionId(1L);
        bo.setItems(List.of(reference));
        return bo;
    }

    private BasketEntryVo resolved() {
        BasketEntryVo entry = new BasketEntryVo();
        entry.setEntryKey("q:1");
        entry.setQuestionId(1L);
        QuestionDetailVo question = new QuestionDetailVo();
        question.setId(1L);
        question.setStemText("frozen");
        entry.setQuestion(question);
        return entry;
    }
}
