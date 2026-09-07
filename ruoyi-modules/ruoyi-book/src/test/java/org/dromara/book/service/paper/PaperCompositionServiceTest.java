package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.bo.CreateExamPaperBo;
import org.dromara.book.domain.bo.PaperQuestionInputBo;
import org.dromara.book.domain.bo.UpdateExamPaperBo;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.domain.entity.BizPaperCategory;
import org.dromara.book.domain.entity.BizPaperCreateRequest;
import org.dromara.book.domain.entity.BizPaperQuestion;
import org.dromara.book.domain.entity.BizPaperSection;
import org.dromara.book.domain.entity.BizQuestionBasketEntry;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.mapper.BizPaperSectionMapper;
import org.dromara.book.mapper.PaperCreateRequestMapper;
import org.dromara.book.mapper.QuestionBasketEntryMapper;
import org.dromara.book.service.IPaperDetailService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperCompositionServiceTest {
    private final BizPaperMapper papers = mock(BizPaperMapper.class);
    private final BizPaperSectionMapper sections = mock(BizPaperSectionMapper.class);
    private final BizPaperQuestionMapper paperQuestions = mock(BizPaperQuestionMapper.class);
    private final BizPaperCategoryMapper categories = mock(BizPaperCategoryMapper.class);
    private final PaperCreateRequestMapper requests = mock(PaperCreateRequestMapper.class);
    private final QuestionBasketEntryMapper basket = mock(QuestionBasketEntryMapper.class);
    private final QuestionSelectionResolver resolver = mock(QuestionSelectionResolver.class);
    private final IPaperDetailService details = mock(IPaperDetailService.class);
    private final QuestionSnapshotCodec codec = new QuestionSnapshotCodec(new ObjectMapper());
    private final PaperCompositionService service = new PaperCompositionService(papers, sections, paperQuestions,
        categories, requests, basket, resolver, codec, details, new ObjectMapper());

    @BeforeAll
    static void initializeMetadata() {
        PaperTestMetadata.initialize();
    }

    @Test
    void createsWithScoresTimeClassificationAndIdempotentRetry() {
        CreateExamPaperBo bo = create();
        bo.setPaperCategoryId("3001004");
        when(categories.selectById("3001004")).thenReturn(new BizPaperCategory());
        when(resolver.resolve(anyList(), eq(9L), eq(false))).thenReturn(List.of(selection("frozen")));
        setupInserts();
        setupRequest();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            var created = service.create(bo, 9L);
            var retry = service.create(bo, 9L);
            assertEquals(created.getPaperId(), retry.getPaperId());
            ArgumentCaptor<BizPaper> paper = ArgumentCaptor.forClass(BizPaper.class);
            verify(papers, times(1)).insert(paper.capture());
            assertEquals("3001004", paper.getValue().getSubjectId());
            assertEquals("3001004", paper.getValue().getPaperCategoryId());
            assertEquals(new BigDecimal("2.25"), paper.getValue().getScore());
            assertEquals(75, paper.getValue().getSuggestTime());
            assertEquals("9", paper.getValue().getCreateBy());
            verify(resolver, times(1)).resolve(anyList(), eq(9L), eq(false));
            bo.setName("different payload");
            assertThrows(ServiceException.class, () -> service.create(bo, 9L));
            verify(papers, times(1)).insert(any(BizPaper.class));
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createFromBasketUsesFrozenEntryNotCurrentSource() {
        CreateExamPaperBo bo = create();
        PaperQuestionInputBo row = bo.getQuestions().get(0);
        row.setSourceBookId(2L);
        row.setSourceItemId(11L);
        row.setBasketNamespace("default");
        BizQuestionBasketEntry stored = new BizQuestionBasketEntry();
        stored.setEntryKey("shelf:11");
        stored.setQuestionId(1L);
        stored.setSourceBookId(2L);
        stored.setSourceItemId(11L);
        stored.setSnapshotJson(codec.encode(selection("at basket time").getQuestion()));
        when(basket.selectList(any())).thenReturn(List.of(stored));
        setupInserts();
        setupRequest();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            service.create(bo, 9L);
        }
        ArgumentCaptor<Collection<BizPaperQuestion>> captor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(paperQuestions).insertBatch(captor.capture());
        BizPaperQuestion saved = captor.getValue().iterator().next();
        assertEquals("at basket time", codec.decode(saved.getSnapshotJson()).getStemText());
        assertEquals(11L, saved.getSourceItemId());
        verify(resolver, never()).resolve(anyList(), anyLong(), anyBoolean());
        verify(resolver).validate(anyList(), eq(9L), eq(false));
    }

    @Test
    void missingBasketEntryAndBadCategoryFailBeforePaperInsert() {
        setupRequest();
        CreateExamPaperBo bo = create();
        bo.getQuestions().get(0).setBasketNamespace("default");
        when(basket.selectList(any())).thenReturn(List.of());
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            assertThrows(ServiceException.class, () -> service.create(bo, 9L));
        }
        verify(papers, never()).insert(any(BizPaper.class));
        bo.setPaperCategoryId("1".repeat(21));
        assertThrows(ServiceException.class, () -> service.create(bo, 9L));
        verifyNoInteractions(categories);
    }

    @Test
    void duplicateInvalidRowsFailBeforeAnyDatabaseWrite() {
        CreateExamPaperBo bo = create();
        bo.setQuestions(List.of(SelectionRulesTest.row(1L, null, 1, "0"),
            SelectionRulesTest.row(1L, null, 2, "0")));
        assertThrows(ServiceException.class, () -> service.create(bo, 9L));
        verifyNoInteractions(papers, requests, paperQuestions, sections);
    }

    @Test
    void updateRejectsOtherOwnerOrSectionBeforeDeletingRows() {
        UpdateExamPaperBo bo = update();
        BizPaper paper = ownedPaper();
        paper.setCreateBy("99");
        when(papers.lockById(10L)).thenReturn(paper);
        assertThrows(ServiceException.class, () -> service.update(bo, 9L));
        paper.setCreateBy("9");
        when(sections.selectList(any())).thenReturn(List.of(section()));
        when(paperQuestions.selectList(any())).thenReturn(List.of());
        bo.getQuestions().get(0).setSectionId(999L);
        assertThrows(ServiceException.class, () -> service.update(bo, 9L));
        verify(paperQuestions, never()).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateRetainsExistingPaperInstanceSnapshot() {
        UpdateExamPaperBo bo = update();
        var input = bo.getQuestions().get(0);
        input.setPaperQuestionId(100L);
        BizPaperQuestion old = new BizPaperQuestion();
        old.setId(100L);
        old.setPaperId(10L);
        old.setQuestionId(1L);
        old.setSectionId(20L);
        old.setSourceBookId(2L);
        old.setSourceItemId(11L);
        old.setSnapshotJson(codec.encode(selection("saved snapshot").getQuestion()));
        when(papers.lockById(10L)).thenReturn(ownedPaper());
        when(sections.selectList(any())).thenReturn(List.of(section()));
        when(paperQuestions.selectList(any())).thenReturn(List.of(old));
        when(paperQuestions.insertBatch(anyCollection())).thenReturn(true);
        when(papers.update(any(), any())).thenReturn(1);
        when(details.getPaperDetail(10L)).thenReturn(new PaperDetailVo());
        service.update(bo, 9L);
        ArgumentCaptor<Collection<BizPaperQuestion>> captor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(paperQuestions).insertBatch(captor.capture());
        BizPaperQuestion saved = captor.getValue().iterator().next();
        assertEquals(100L, saved.getId());
        assertEquals(11L, saved.getSourceItemId());
        assertEquals("saved snapshot", codec.decode(saved.getSnapshotJson()).getStemText());
        assertEquals(new BigDecimal("3.50"), saved.getScore());
        verifyNoInteractions(resolver);
    }

    @Test
    void updateAllowsUnpublishedOwnerPaperWithoutChangingItsStatus() {
        UpdateExamPaperBo bo = update();
        bo.getQuestions().get(0).setPaperQuestionId(100L);
        BizPaper paper = ownedPaper();
        paper.setStatus("0");
        BizPaperQuestion old = new BizPaperQuestion();
        old.setId(100L);
        old.setPaperId(10L);
        old.setQuestionId(1L);
        old.setSectionId(20L);
        old.setSnapshotJson(codec.encode(selection("draft snapshot").getQuestion()));
        when(papers.lockById(10L)).thenReturn(paper);
        when(sections.selectList(any())).thenReturn(List.of(section()));
        when(paperQuestions.selectList(any())).thenReturn(List.of(old));
        when(paperQuestions.insertBatch(anyCollection())).thenReturn(true);
        when(papers.update(org.mockito.ArgumentMatchers.<BizPaper>isNull(), any())).thenReturn(1);
        when(details.getPaperDetail(10L)).thenReturn(new PaperDetailVo());

        service.update(bo, 9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BizPaper>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(papers).update(org.mockito.ArgumentMatchers.<BizPaper>isNull(), update.capture());
        assertEquals("0", paper.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(update.getValue().getSqlSet().contains("status"));
    }

    private void setupRequest() {
        AtomicReference<BizPaperCreateRequest> state = new AtomicReference<>();
        doAnswer(invocation -> {
            if (state.get() == null) {
                BizPaperCreateRequest request = new BizPaperCreateRequest();
                request.setPayloadHash(invocation.getArgument(2));
                state.set(request);
            }
            return null;
        }).when(requests).ensureRequest(eq(9L), anyString(), anyString());
        when(requests.lockRequest(eq(9L), anyString())).thenAnswer(invocation -> state.get());
        when(requests.complete(eq(9L), anyString(), eq(10L), anyInt())).thenAnswer(invocation -> {
            state.get().setPaperId(10L);
            state.get().setQuestionCount(invocation.getArgument(3));
            return 1;
        });
    }

    private void setupInserts() {
        when(papers.insert(any(BizPaper.class))).thenAnswer(invocation -> {
            BizPaper paper = invocation.getArgument(0);
            paper.setId(10L);
            return 1;
        });
        when(papers.lockById(10L)).thenReturn(ownedPaper());
        when(sections.insert(any(BizPaperSection.class))).thenAnswer(invocation -> {
            BizPaperSection section = invocation.getArgument(0);
            section.setId(20L);
            return 1;
        });
        when(paperQuestions.insertBatch(anyCollection())).thenReturn(true);
    }

    private CreateExamPaperBo create() {
        CreateExamPaperBo bo = new CreateExamPaperBo();
        bo.setName("paper test");
        bo.setRequestId(UUID.randomUUID().toString());
        bo.setSuggestTime(75);
        bo.setQuestions(List.of(SelectionRulesTest.row(1L, null, 1, "2.25")));
        return bo;
    }

    private UpdateExamPaperBo update() {
        UpdateExamPaperBo bo = new UpdateExamPaperBo();
        bo.setPaperId(10L);
        var row = new UpdateExamPaperBo.UpdateExamPaperQuestionBo();
        row.setQuestionId(1L);
        row.setSectionId(20L);
        row.setSort(1);
        row.setScore(new BigDecimal("3.50"));
        bo.setQuestions(List.of(row));
        return bo;
    }

    private BizPaper ownedPaper() {
        BizPaper paper = new BizPaper();
        paper.setId(10L);
        paper.setCreateBy("9");
        paper.setStatus("1");
        return paper;
    }

    private BizPaperSection section() {
        BizPaperSection section = new BizPaperSection();
        section.setId(20L);
        section.setPaperId(10L);
        section.setSort(1);
        return section;
    }

    private BasketEntryVo selection(String text) {
        QuestionDetailVo question = new QuestionDetailVo();
        question.setId(1L);
        question.setStemText(text);
        BasketEntryVo entry = new BasketEntryVo();
        entry.setEntryKey("q:1");
        entry.setQuestionId(1L);
        entry.setQuestion(question);
        return entry;
    }
}
