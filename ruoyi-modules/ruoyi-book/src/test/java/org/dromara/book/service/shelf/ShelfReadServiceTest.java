package org.dromara.book.service.shelf;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.book.domain.bo.ShelfItemPageBo;
import org.dromara.book.domain.entity.BizShelfBook;
import org.dromara.book.domain.entity.BizShelfItem;
import org.dromara.book.domain.entity.BizShelfNode;
import org.dromara.book.domain.vo.ShelfReadVo;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizShelfBookMapper;
import org.dromara.book.mapper.BizShelfItemMapper;
import org.dromara.book.mapper.BizShelfNodeMapper;
import org.dromara.book.mapper.BizSubjectMapper;
import org.dromara.book.service.paper.QuestionSnapshotCodec;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** No database writes or Spring server; the coordinator runs this with the integration build. */
@Tag("dev")
@SuppressWarnings("unchecked")
class ShelfReadServiceTest {
    private final BizShelfBookMapper books = mock(BizShelfBookMapper.class);
    private final BizShelfNodeMapper nodes = mock(BizShelfNodeMapper.class);
    private final BizShelfItemMapper items = mock(BizShelfItemMapper.class);
    private final ShelfService service = new ShelfService(books, nodes, items,
        mock(BizQuestionMapper.class), mock(BizSubjectMapper.class), mock(BizCoursePlanLessonMapper.class),
        new QuestionSnapshotCodec(new ObjectMapper()));

    @BeforeAll
    static void configureMetadata() {
        MapperBuilderAssistant builder = new MapperBuilderAssistant(new MybatisConfiguration(), "shelf-test");
        for (Class<?> type : List.of(BizShelfBook.class, BizShelfNode.class, BizShelfItem.class)) {
            TableInfoHelper.initTableInfo(builder, type);
        }
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            assertNotNull(JsonUtils.getObjectMapper());
        }
    }

    @Test
    void outlineAggregatesSubtreeWithoutReadingContent() throws Exception {
        when(books.selectById(1L)).thenReturn(book(1L));
        BizShelfNode root = node(10L, null);
        BizShelfNode child = node(11L, 10L);
        child.setMetaJson("{\"body\":\"not in outline\"}");
        when(nodes.selectPage(any(Page.class), any())).thenReturn(nodePage(root, child));
        when(items.selectNodeCounts(1L)).thenReturn(List.of(counts(10L, 1, 1), counts(11L, 3, 2)));

        ShelfReadVo.Outline result = service.getOutline(1L);

        assertEquals(1, result.getTree().size());
        assertEquals(3, result.getTree().get(0).getQuestionCount());
        assertEquals(1, result.getTree().get(0).getItemCount());
        assertEquals(2, result.getTree().get(0).getChildren().get(0).getQuestionCount());
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("questionId"));
        assertFalse(json.contains("override"));
        assertFalse(json.contains("not in outline"));
        assertTrue(json.contains("\"isPublic\":true"));
        verify(items, never()).selectPage(any(Page.class), any());
        verify(items, never()).selectReadingQuestions(any());
    }

    @Test
    void outlineRejectsDetachedCycle() {
        when(books.selectById(1L)).thenReturn(book(1L));
        when(nodes.selectPage(any(Page.class), any())).thenReturn(nodePage(node(10L, 11L), node(11L, 10L)));
        when(items.selectNodeCounts(1L)).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.getOutline(1L));
    }

    @Test
    void outlineRejectsMissingParentIncludingUnsupportedZeroRoot() {
        when(books.selectById(1L)).thenReturn(book(1L));
        when(nodes.selectPage(any(Page.class), any())).thenReturn(nodePage(node(10L, 0L)));
        when(items.selectNodeCounts(1L)).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.getOutline(1L));
    }

    @Test
    void invalidPageNeverReachesDatabase() {
        ShelfItemPageBo request = new ShelfItemPageBo();
        request.setPageSize(101);
        assertThrows(ServiceException.class, () -> service.getNodeItems(1L, 10L, request));
        request.setPageSize(20);
        request.setPageNum(0);
        assertThrows(ServiceException.class, () -> service.getNodeItems(1L, 10L, request));
        verifyNoInteractions(books, nodes, items);
    }

    @Test
    void privateBookDeniesNonOwnerAndOwnerlessRead() {
        BizShelfBook book = book(1L);
        book.setIsPublic(0);
        book.setOwnerId(99L);
        when(books.selectById(1L)).thenReturn(book);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            assertThrows(ServiceException.class, () -> service.getOutline(1L));
            book.setOwnerId(null);
            assertThrows(ServiceException.class, () -> service.getOutline(1L));
        }
        verifyNoInteractions(nodes, items);
    }

    @Test
    void foreignNodeCannotBeReadThroughPublicBook() {
        when(books.selectById(1L)).thenReturn(book(1L));
        BizShelfNode foreign = node(10L, null);
        foreign.setBookId(2L);
        when(nodes.selectById(10L)).thenReturn(foreign);
        assertThrows(ServiceException.class, () -> service.getNodeItems(1L, 10L, new ShelfItemPageBo()));
        verifyNoInteractions(items);
    }

    @Test
    void pagePreservesInstancesBodiesAndExplicitMissingQuestion() {
        when(books.selectById(1L)).thenReturn(book(1L));
        BizShelfNode node = node(10L, null);
        node.setMetaJson("{\"layout\":\"document\"}");
        when(nodes.selectById(10L)).thenReturn(node);
        BizShelfItem first = item(100L, 9007199254740993L, "question");
        first.setOverrideJson("{\"stem\":\"\",\"options\":[],\"answer\":\"override-answer\"}");
        BizShelfItem second = item(101L, 9007199254740993L, "question");
        BizShelfItem missing = item(102L, 999L, "question");
        BizShelfItem module = item(103L, null, "module");
        module.setContentJson("{\"type\":\"oral\",\"items\":[{\"q\":\"1+1\",\"a\":\"2\"}]}");
        module.setSourcePage(7);
        Page<BizShelfItem> page = new Page<>(1, 4, 5);
        page.setRecords(List.of(first, second, missing, module));
        when(items.selectCount(any())).thenReturn(5L);
        when(items.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<?> inputPage = invocation.getArgument(0);
            assertFalse(inputPage.searchCount());
            LambdaQueryWrapper<BizShelfItem> query = invocation.getArgument(1);
            assertTrue(query.getSqlSegment().contains("ORDER BY seq ASC,id ASC"));
            assertTrue(query.getSqlSelect().contains("content_json"));
            return page;
        });
        ShelfReadVo.Question question = new ShelfReadVo.Question();
        question.setId("9007199254740993");
        question.setStemTextContent("original");
        question.setBlockJson("{\"v\":1,\"rows\":[]}");
        question.setAnswerTextContent("answer");
        question.setAnswerImg("https://example.test/answer.png");
        when(items.selectReadingQuestions(List.of(9007199254740993L, 999L))).thenReturn(List.of(question));
        ShelfItemPageBo request = new ShelfItemPageBo();
        request.setPageSize(4);

        ShelfReadVo.ItemPage result = service.getNodeItems(1L, 10L, request);

        assertEquals(4, result.getRows().size());
        assertTrue(result.isHasMore());
        assertEquals("", result.getRows().get(0).getOverride().get("stem").asText());
        assertNull(result.getRows().get(1).getOverride());
        assertEquals("", result.getRows().get(0).getQuestion().getStemTextContent());
        assertEquals("original", result.getRows().get(0).getOriginalStemText());
        assertEquals("override-answer", result.getRows().get(0).getQuestion().getAnswerTextContent());
        assertEquals("answer", result.getRows().get(1).getQuestion().getAnswerTextContent());
        assertTrue(result.getRows().get(2).isQuestionMissing());
        assertFalse(result.getRows().get(3).isQuestionMissing());
        assertEquals("oral", result.getRows().get(3).getContent().get("type").asText());
        assertEquals(7, result.getRows().get(3).getSourcePage());
        assertEquals("document", result.getNode().getMeta().get("layout").asText());
        verify(items, times(1)).selectReadingQuestions(any());
    }

    @Test
    void changedStemOrOptionsWithoutAnswerNeverReuseOriginalSolution() {
        when(books.selectById(1L)).thenReturn(book(1L));
        when(nodes.selectById(10L)).thenReturn(node(10L, null));
        BizShelfItem stemChanged = item(100L, 200L, "question");
        stemChanged.setOverrideJson("{\"stem\":\"new problem\"}");
        BizShelfItem optionsChanged = item(101L, 200L, "question");
        optionsChanged.setOverrideJson("{\"options\":[\"new option\"]}");
        Page<BizShelfItem> page = new Page<>(1, 20, 2);
        page.setRecords(List.of(stemChanged, optionsChanged));
        when(items.selectCount(any())).thenReturn(2L);
        when(items.selectPage(any(Page.class), any())).thenReturn(page);
        ShelfReadVo.Question original = new ShelfReadVo.Question();
        original.setId("200");
        original.setStemText("original problem");
        original.setAnswerTextContent("original answer");
        original.setAnalyzeTextContent("original analysis");
        original.setAnswerImg("https://example.test/answer.png");
        original.setExplainImg("https://example.test/analysis.png");
        original.setAnswerBlockJson("{\"v\":1,\"rows\":[]}");
        original.setAnalyzeBlockJson("{\"v\":1,\"rows\":[]}");
        when(items.selectReadingQuestions(List.of(200L))).thenReturn(List.of(original));

        ShelfReadVo.ItemPage result = service.getNodeItems(1L, 10L, new ShelfItemPageBo());

        for (ShelfReadVo.Item row : result.getRows()) {
            assertNull(row.getQuestion().getAnswerTextContent());
            assertNull(row.getQuestion().getAnalyzeTextContent());
            assertNull(row.getQuestion().getAnswerImg());
            assertNull(row.getQuestion().getExplainImg());
            assertNull(row.getQuestion().getAnswerBlockJson());
            assertNull(row.getQuestion().getAnalyzeBlockJson());
        }
        assertEquals("original answer", original.getAnswerTextContent());
        assertEquals("original analysis", original.getAnalyzeTextContent());
    }

    @Test
    void emptyPageReturnsMetadataWithoutQuestionLookup() {
        when(books.selectById(1L)).thenReturn(book(1L));
        when(nodes.selectById(10L)).thenReturn(node(10L, null));
        when(items.selectCount(any())).thenReturn(1L);
        ShelfItemPageBo request = new ShelfItemPageBo();
        request.setPageNum(2);
        ShelfReadVo.ItemPage result = service.getNodeItems(1L, 10L, request);
        assertTrue(result.getRows().isEmpty());
        assertFalse(result.isHasMore());
        assertEquals("10", result.getNode().getId());
        verify(items, never()).selectPage(any(Page.class), any());
        verify(items, never()).selectReadingQuestions(any());
    }

    @Test
    void shelfListUsesTwoAggregateQueriesNotThreeCountsPerBook() {
        when(books.selectList(any())).thenReturn(List.of(book(1L), book(2L)));
        ShelfReadVo.Counts nodeCount = counts(1L, 0, 0);
        nodeCount.setNodeCount(8);
        when(nodes.selectBookCounts(List.of(1L, 2L))).thenReturn(List.of(nodeCount));
        when(items.selectBookCounts(List.of(1L, 2L))).thenReturn(List.of(counts(1L, 12, 10)));
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            Map<String, Object> result = service.pageBooks(null, null, null);
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            assertEquals(8L, rows.get(0).get("nodeCount"));
            assertEquals(12L, rows.get(0).get("itemCount"));
            assertEquals(10L, rows.get(0).get("questionCount"));
            assertEquals(0L, rows.get(1).get("questionCount"));
        }
        verify(nodes, times(1)).selectBookCounts(any());
        verify(items, times(1)).selectBookCounts(any());
        verify(nodes, never()).selectCount(any());
        verify(items, never()).selectCount(any());
    }

    private static BizShelfBook book(Long id) {
        BizShelfBook book = new BizShelfBook();
        book.setId(id);
        book.setBookType("lecture");
        book.setTitle("Book " + id);
        book.setIsPublic(1);
        return book;
    }

    private static BizShelfNode node(Long id, Long parentId) {
        BizShelfNode node = new BizShelfNode();
        node.setId(id);
        node.setBookId(1L);
        node.setParentId(parentId);
        node.setName("Node " + id);
        node.setSeq(1);
        return node;
    }

    private static Page<BizShelfNode> nodePage(BizShelfNode... nodes) {
        Page<BizShelfNode> page = new Page<>();
        page.setRecords(List.of(nodes));
        return page;
    }

    private static BizShelfItem item(Long id, Long questionId, String kind) {
        BizShelfItem item = new BizShelfItem();
        item.setId(id);
        item.setNodeId(10L);
        item.setBookId(1L);
        item.setSeq(1);
        item.setKind(kind);
        item.setQuestionId(questionId);
        return item;
    }

    private static ShelfReadVo.Counts counts(Long id, long itemCount, long questionCount) {
        ShelfReadVo.Counts counts = new ShelfReadVo.Counts();
        counts.setId(id);
        counts.setItemCount(itemCount);
        counts.setQuestionCount(questionCount);
        return counts;
    }
}
