package org.dromara.book.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.entity.BizPaperQuestion;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperSectionVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.service.IQuestionService;
import org.dromara.book.service.impl.PaperDetailServiceImpl;
import org.dromara.book.service.impl.PaperSourceServiceImpl;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperDetailServiceTest {
    private final BizPaperMapper papers = mock(BizPaperMapper.class);
    private final BizPaperQuestionMapper rows = mock(BizPaperQuestionMapper.class);
    private final IQuestionService questions = mock(IQuestionService.class);
    private final QuestionSnapshotCodec codec = new QuestionSnapshotCodec(new ObjectMapper());
    private final PaperDetailServiceImpl service = new PaperDetailServiceImpl(papers, rows, questions, codec);

    @BeforeAll
    static void initializeMetadata() {
        PaperTestMetadata.initialize();
    }

    @Test
    void snapshotReadAndSourcePreviewDoNotHydrateOriginals() {
        QuestionDetailVo snapshot = new QuestionDetailVo();
        snapshot.setId(1L);
        snapshot.setStemText("frozen book stem");
        snapshot.setAnswer(null);
        snapshot.setExplain("new explanation");
        snapshot.setStemImg("https://example.test/frozen.png");
        BizPaperQuestion record = new BizPaperQuestion();
        record.setId(100L);
        record.setQuestionId(1L);
        record.setSectionId(20L);
        record.setSourceBookId(2L);
        record.setSourceItemId(11L);
        record.setScore(new BigDecimal("2.25"));
        record.setSort(1);
        record.setSnapshotJson(codec.encode(snapshot));
        PaperDetailVo header = new PaperDetailVo();
        header.setPaperId(10L);
        header.setSuggestTime(75);
        header.setScore(new BigDecimal("2.25"));
        PaperSectionVo section = new PaperSectionVo();
        section.setSectionId(20L);
        when(papers.selectPaperDetailHeader(10L, "9")).thenReturn(header);
        when(papers.selectSectionsByPaperId(10L)).thenReturn(List.of(section));
        when(rows.selectList(any())).thenReturn(List.of(record));
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            var detail = service.getPaperDetail(10L);
            var actual = detail.getSections().get(0).getQuestions().get(0);
            assertEquals("frozen book stem", actual.getStemText());
            assertNull(actual.getAnswer());
            assertEquals("new explanation", actual.getExplain());
            assertEquals("https://example.test/frozen.png", actual.getStemImg());
            assertEquals(100L, actual.getPaperQuestionId());
            assertEquals(11L, actual.getSourceItemId());
            assertEquals(new BigDecimal("2.25"), actual.getPqScore());
            var source = new PaperSourceServiceImpl(service).getPaperSource(10L);
            assertEquals(actual, source.getQuestions().get(0));
            assertEquals(75, source.getSuggestTime());
            assertEquals(new BigDecimal("2.25"), source.getScore());
        }
        verifyNoInteractions(questions);
    }

    @Test
    void deniedHeaderNeverLoadsPrivateRows() {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(99L);
            when(papers.selectPaperDetailHeader(10L, "99")).thenReturn(null);
            assertNull(service.getPaperDetail(10L));
            assertNull(new PaperSourceServiceImpl(service).getPaperSource(10L));
        }
        verifyNoInteractions(rows, questions);
    }
}
