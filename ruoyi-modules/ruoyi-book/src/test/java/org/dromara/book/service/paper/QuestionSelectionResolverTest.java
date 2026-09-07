package org.dromara.book.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.bo.SelectionReferenceBo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.QuestionSelectionMapper;
import org.dromara.book.service.IQuestionService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class QuestionSelectionResolverTest {
    private final QuestionSelectionMapper mapper = mock(QuestionSelectionMapper.class);
    private final IQuestionService questions = mock(IQuestionService.class);
    private final QuestionSelectionResolver resolver = new QuestionSelectionResolver(mapper, questions,
        new QuestionSnapshotCodec(new ObjectMapper()));

    @Test
    void privateOriginalFailsBeforeContentLoad() {
        when(mapper.selectReadableQuestionIds(List.of(1L), 9L, false)).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> resolver.resolve(List.of(ref(1L, null)), 9L, false));
        verifyNoInteractions(questions);
    }

    @Test
    void publicBookAuthorizesReferenceToNonPublicQuestion() {
        QuestionSelectionMapper.ShelfSelectionRow item = item();
        when(mapper.selectShelfItems(List.of(11L))).thenReturn(List.of(item));
        when(questions.listByIds(List.of(1L))).thenReturn(List.of(question(1L)));
        var resolved = resolver.resolve(List.of(ref(1L, 11L)), 9L, false);
        assertEquals("shelf:11", resolved.get(0).getEntryKey());
        assertEquals("book stem", resolved.get(0).getQuestion().getStemText());
        verify(mapper, never()).selectReadableQuestionIds(anyList(), eq(9L), eq(false));
    }

    @Test
    void mismatchedSourceAndPrivateBookFailBeforeLoad() {
        QuestionSelectionMapper.ShelfSelectionRow item = item();
        item.setQuestionId(2L);
        when(mapper.selectShelfItems(List.of(11L))).thenReturn(List.of(item));
        assertThrows(ServiceException.class, () -> resolver.resolve(List.of(ref(1L, 11L)), 9L, false));
        item.setQuestionId(1L);
        item.setIsPublic(0);
        assertThrows(ServiceException.class, () -> resolver.resolve(List.of(ref(1L, 11L)), 9L, false));
        verifyNoInteractions(questions);
    }

    @Test
    void largeSelectionUsesBoundedSequentialRenderBatches() {
        List<Long> ids = LongStream.rangeClosed(1, 201).boxed().toList();
        when(mapper.selectReadableQuestionIds(ids, 9L, false)).thenReturn(ids);
        when(questions.listByIds(anyList())).thenAnswer(invocation -> {
            List<Long> batch = invocation.getArgument(0);
            if (batch.size() > 100) {
                throw new AssertionError("unbounded batch");
            }
            return batch.stream().map(this::question).toList();
        });
        var resolved = resolver.resolve(ids.stream().map(id -> ref(id, null)).toList(), 9L, false);
        assertEquals(201, resolved.size());
        assertEquals(201L, resolved.get(200).getQuestionId());
        verify(questions, times(3)).listByIds(anyList());
    }

    private SelectionReferenceBo ref(Long questionId, Long itemId) {
        SelectionReferenceBo ref = new SelectionReferenceBo();
        ref.setQuestionId(questionId);
        ref.setSourceItemId(itemId);
        ref.setSourceBookId(itemId == null ? null : 2L);
        return ref;
    }

    private QuestionSelectionMapper.ShelfSelectionRow item() {
        var item = new QuestionSelectionMapper.ShelfSelectionRow();
        item.setId(11L);
        item.setQuestionId(1L);
        item.setBookId(2L);
        item.setBookStatus("0");
        item.setKind("question");
        item.setOwnerId(99L);
        item.setIsPublic(1);
        item.setOverrideJson("{\"stem\":\"book stem\"}");
        return item;
    }

    private QuestionDetailVo question(Long id) {
        QuestionDetailVo question = new QuestionDetailVo();
        question.setId(id);
        question.setStatus("1");
        question.setStemText("original");
        return question;
    }
}
