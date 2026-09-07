package org.dromara.book.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.annotations.Select;
import org.dromara.book.controller.MisiktEnvelopeAdvice;
import org.dromara.book.controller.QuestionBasketEntryController;
import org.dromara.book.domain.vo.BasketKeysVo;
import org.dromara.book.mapper.QuestionBasketEntryMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class QuestionBasketMembershipTest {
    private final QuestionBasketEntryMapper entries = mock(QuestionBasketEntryMapper.class);
    private final QuestionSelectionResolver resolver = mock(QuestionSelectionResolver.class);
    private final QuestionSnapshotCodec codec = mock(QuestionSnapshotCodec.class);
    private final QuestionBasketEntryService service = new QuestionBasketEntryService(entries, resolver, codec);

    @Test
    void membershipIsIsolatedByCurrentUserAndNamespaceWithoutLoadingSnapshots() {
        when(entries.selectEntryKeys(9L, "lesson:123", 500)).thenReturn(List.of("q:1", "shelf:11"));
        when(entries.selectEntryKeys(9L, "default", 500)).thenReturn(List.of("q:2"));
        when(entries.selectEntryKeys(10L, "lesson:123", 500)).thenReturn(List.of("q:3"));
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertEquals(List.of("q:1", "shelf:11"), service.keys("lesson:123").entryKeys());
            assertEquals(List.of("q:2"), service.keys(null).entryKeys());
            login.when(LoginHelper::getUserId).thenReturn(10L);
            assertEquals(List.of("q:3"), service.keys("lesson:123").entryKeys());
        }
        verify(entries).selectEntryKeys(9L, "lesson:123", 500);
        verify(entries).selectEntryKeys(9L, "default", 500);
        verify(entries).selectEntryKeys(10L, "lesson:123", 500);
        verifyNoMoreInteractions(entries);
        verifyNoInteractions(resolver, codec);
    }

    @Test
    void anonymousAndInvalidNamespaceFailBeforeQuerying() {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(null);
            assertEquals(401, assertThrows(ServiceException.class, () -> service.keys("default")).getCode());
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertEquals(400, assertThrows(ServiceException.class, () -> service.keys("../other")).getCode());
            assertEquals(400, assertThrows(ServiceException.class, () -> service.keys("x".repeat(65))).getCode());
        }
        verifyNoInteractions(entries, resolver, codec);
    }

    @Test
    void fullCapacityMembershipPreservesOrderBeyondTheRenderedPage() {
        List<String> allKeys = IntStream.rangeClosed(1, 500).mapToObj(id -> "q:" + id).toList();
        when(entries.selectEntryKeys(9L, "default", SelectionRules.MAX_PAPER_QUESTIONS)).thenReturn(allKeys);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            BasketKeysVo result = service.keys("default");
            assertEquals(500, result.entryKeys().size());
            assertEquals("q:1", result.entryKeys().get(0));
            assertEquals("q:500", result.entryKeys().get(499));
        }
        verifyNoInteractions(resolver, codec);
    }

    @Test
    void mapperUsesOnlyBoundedOrderedKeyProjection() throws Exception {
        Select select = QuestionBasketEntryMapper.class.getMethod("selectEntryKeys", Long.class, String.class, int.class)
            .getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        assertTrue(sql.startsWith("select entry_key from biz_question_basket_entry"));
        assertTrue(sql.contains("user_id = #{userid} and namespace = #{namespace}"));
        assertTrue(sql.contains("order by id asc limit #{limit}"));
        assertFalse(sql.contains("snapshot_json"));
        assertFalse(sql.contains("*"));
    }

    @Test
    void endpointSerializesOnlyStringEntryKeysAndEmptyArray() throws Exception {
        List<String> keys = List.of("q:1", "shelf:2077049541005160450");
        when(entries.selectEntryKeys(9L, "default", 500)).thenReturn(keys);
        var mvc = MockMvcBuilders.standaloneSetup(new QuestionBasketEntryController(service))
            .setControllerAdvice(new MisiktEnvelopeAdvice()).build();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            mvc.perform(get("/teacher/question/basket/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.response.entryKeys[0]").value("q:1"))
                .andExpect(jsonPath("$.response.entryKeys[1]").value("shelf:2077049541005160450"))
                .andExpect(jsonPath("$.response.question").doesNotExist())
                .andExpect(jsonPath("$.response.snapshotJson").doesNotExist());
        }
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("{\"entryKeys\":[]}", mapper.writeValueAsString(new BasketKeysVo(List.of())));
        assertEquals("{\"entryKeys\":[\"q:1\",\"shelf:2077049541005160450\"]}",
            mapper.writeValueAsString(new BasketKeysVo(keys)));
        verifyNoInteractions(resolver, codec);
    }
}
