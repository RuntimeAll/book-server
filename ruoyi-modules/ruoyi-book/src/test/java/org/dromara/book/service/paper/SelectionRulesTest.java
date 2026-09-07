package org.dromara.book.service.paper;

import org.dromara.book.domain.bo.PaperQuestionInputBo;
import org.dromara.book.domain.bo.SelectionReferenceBo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class SelectionRulesTest {
    @Test
    void identityIsInstanceNotQuestion() {
        PaperQuestionInputBo original = row(1L, null, 1, "1.25");
        PaperQuestionInputBo first = row(1L, 11L, 2, "999.99");
        PaperQuestionInputBo second = row(1L, 12L, 3, "0");
        assertEquals("q:1", SelectionRules.entryKey(original));
        assertEquals("shelf:11", SelectionRules.entryKey(first));
        assertDoesNotThrow(() -> SelectionRules.validateRows(List.of(original, first, second)));
        second.setSourceItemId(11L);
        assertThrows(ServiceException.class, () -> SelectionRules.validateRows(List.of(first, second)));
    }

    @Test
    void rejectsIncompleteSourceAndInvalidIdentifiers() {
        SelectionReferenceBo ref = new SelectionReferenceBo();
        ref.setQuestionId(1L);
        ref.setSourceBookId(2L);
        assertThrows(ServiceException.class, () -> SelectionRules.entryKey(ref));
        ref.setSourceItemId(-1L);
        assertThrows(ServiceException.class, () -> SelectionRules.entryKey(ref));
        assertThrows(ServiceException.class, () -> SelectionRules.entryKey(null));
    }

    @Test
    void rejectsInvalidScoresSortsAndCountBeforeWriting() {
        for (String score : List.of("-0.01", "1000", "1.001")) {
            assertThrows(ServiceException.class, () -> SelectionRules.validateRows(List.of(row(1L, null, 1, score))));
        }
        assertThrows(ServiceException.class, () -> SelectionRules.validateRows(List.of(row(1L, null, 0, "1"))));
        assertThrows(ServiceException.class, () -> SelectionRules.validateRows(List.of(row(1L, null, 501, "1"))));
        assertThrows(ServiceException.class, () -> SelectionRules.validateRows(List.of()));
        assertThrows(ServiceException.class, () -> SelectionRules.validateRows(
            List.of(row(1L, null, 1, "1"), row(2L, null, 1, "1"))));
    }

    @Test
    void validatesNamespaceTimeAndIdempotencyKey() {
        assertEquals("lesson:123_2", SelectionRules.namespace("lesson:123_2"));
        assertThrows(ServiceException.class, () -> SelectionRules.namespace("../other"));
        assertThrows(ServiceException.class, () -> SelectionRules.namespace("x".repeat(65)));
        assertDoesNotThrow(() -> SelectionRules.validateTime(1440));
        assertThrows(ServiceException.class, () -> SelectionRules.validateTime(0));
        assertThrows(ServiceException.class, () -> SelectionRules.validateTime(1441));
        assertDoesNotThrow(() -> SelectionRules.validateRequestId(UUID.randomUUID().toString(), true));
        assertThrows(ServiceException.class, () -> SelectionRules.validateRequestId("1-1-1-1-1", true));
        assertThrows(ServiceException.class, () -> SelectionRules.validateRequestId(null, true));
        assertDoesNotThrow(() -> SelectionRules.validateRequestId(null, false));
    }

    static PaperQuestionInputBo row(Long qid, Long itemId, int sort, String score) {
        PaperQuestionInputBo row = new PaperQuestionInputBo();
        row.setQuestionId(qid);
        row.setSourceItemId(itemId);
        row.setSourceBookId(itemId == null ? null : 2L);
        row.setSort(sort);
        row.setScore(new BigDecimal(score));
        return row;
    }
}
