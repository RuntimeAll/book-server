package org.dromara.book.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.bo.QuestionBatchBo;
import org.dromara.book.domain.vo.QuestionBatchVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.QuestionSelectionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class QuestionBatchServiceTest {
    private final IQuestionService questions = mock(IQuestionService.class);
    private final QuestionSelectionMapper selections = mock(QuestionSelectionMapper.class);
    private final QuestionBatchService service = new QuestionBatchService(questions, selections);

    @Test
    void preservesDistinctOrderAndReportsMissingSnowflakeIds() {
        long snowflake = 2077068347467378689L;
        QuestionDetailVo first = question(snowflake);
        QuestionDetailVo second = question(2L);
        when(selections.selectReadableQuestionIds(List.of(snowflake, 2L, 3L), 7L, false))
            .thenReturn(List.of(snowflake, 2L, 3L));
        when(questions.listByIds(List.of(snowflake, 2L, 3L))).thenReturn(List.of(second, first));
        QuestionBatchVo result = query(request(List.of(snowflake, 2L, snowflake, 3L)));
        assertEquals(List.of(first, second), result.items());
        assertEquals(List.of("3"), result.missingIds());
        verify(questions).listByIds(List.of(snowflake, 2L, 3L));
    }

    @Test
    void emptySelectionDoesNotReadDatabase() {
        assertEquals(List.of(), query(request(List.of())).items());
        verifyNoInteractions(questions, selections);
    }

    @Test
    void identifiersAreJsonStringsEvenBelowJavascriptPrecisionLimit() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        QuestionBatchVo response = new QuestionBatchVo(List.of(question(7L)), List.of("9"));
        String json = mapper.writeValueAsString(response);
        assertEquals("7", mapper.readTree(json).path("items").get(0).path("id").textValue());
        assertEquals("9", mapper.readTree(json).path("missingIds").get(0).textValue());
    }

    @Test
    void rejectsInvalidAndUnboundedRequestsBeforeQuery() {
        assertThrows(ServiceException.class, () -> query(null));
        assertThrows(ServiceException.class, () -> query(request(null)));
        assertThrows(ServiceException.class, () -> query(request(Arrays.asList(1L, null))));
        assertThrows(ServiceException.class, () -> query(request(List.of(0L))));
        assertThrows(ServiceException.class,
            () -> query(request(LongStream.rangeClosed(1, 101).boxed().toList())));
        verifyNoInteractions(questions, selections);
    }

    @Test
    void privateQuestionsAreNotLoadedAndAnonymousCallFails() {
        when(selections.selectReadableQuestionIds(List.of(1L), 7L, false)).thenReturn(List.of());
        assertEquals(List.of("1"), query(request(List.of(1L))).missingIds());
        assertThrows(ServiceException.class, () -> service.query(request(List.of(1L)), null, false));
        verifyNoInteractions(questions);
    }

    private QuestionBatchVo query(QuestionBatchBo bo) {
        return service.query(bo, 7L, false);
    }

    private QuestionBatchBo request(List<Long> ids) {
        QuestionBatchBo bo = new QuestionBatchBo();
        bo.setIds(ids);
        return bo;
    }

    private QuestionDetailVo question(Long id) {
        QuestionDetailVo question = new QuestionDetailVo();
        question.setId(id);
        return question;
    }
}
