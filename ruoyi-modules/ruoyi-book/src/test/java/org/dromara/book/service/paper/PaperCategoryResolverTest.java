package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.book.domain.entity.BizPaperCategory;
import org.dromara.book.domain.vo.BasketEntryVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.PaperClassificationMapper;
import org.dromara.book.mapper.PaperClassificationMapper.BookSubject;
import org.dromara.book.mapper.PaperClassificationMapper.SubjectDimensions;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperCategoryResolverTest {
    private final PaperClassificationMapper source = mock(PaperClassificationMapper.class);
    private final BizPaperCategoryMapper categories = mock(BizPaperCategoryMapper.class);
    private final PaperCategoryResolver resolver = new PaperCategoryResolver(source, categories);

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "category-test");
        assistant.setCurrentNamespace(BizPaperCategory.class.getName());
        TableInfoHelper.initTableInfo(assistant, BizPaperCategory.class);
    }

    @Test
    void mapsDifferentChaptersToTheSameGradeAndVolume() {
        when(source.selectSubjectDimensions(anyList())).thenReturn(List.of(
            dimension("chapter-a", 4, 1), dimension("chapter-b", 4, 1)));
        when(categories.selectList(any())).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> query = call.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("volume ="));
            assertTrue(sql.contains("grade ="));
            assertTrue(query.getParamNameValuePairs().containsValue(4));
            assertTrue(query.getParamNameValuePairs().containsValue("3001"));
            return List.of(category("grade-four-up"));
        });
        assertEquals("grade-four-up", resolver.infer(List.of(entry("chapter-a"), entry("chapter-b"))));
    }

    @Test
    void usesSourceBookCurriculumInsteadOfOriginalQuestionCurriculum() {
        BasketEntryVo entry = entry("original-other-grade");
        entry.setSourceBookId(20L);
        BookSubject book = new BookSubject();
        book.setId(20L);
        book.setSubjectId("book-root");
        when(source.selectBookSubjects(List.of(20L))).thenReturn(List.of(book));
        when(source.selectSubjectDimensions(List.of("book-root"))).thenReturn(List.of(dimension("book-root", 4, 1)));
        when(categories.selectList(any())).thenReturn(List.of(category("four-up")));
        assertEquals("four-up", resolver.infer(List.of(entry)));
        verify(source).selectSubjectDimensions(List.of("book-root"));
    }

    @Test
    void fallsBackToQuestionWhenBookHasNoCurriculum() {
        BasketEntryVo entry = entry("question-root");
        entry.setSourceBookId(20L);
        when(source.selectBookSubjects(anyList())).thenReturn(List.of());
        when(source.selectSubjectDimensions(anyList())).thenReturn(List.of(dimension("question-root", 4, 1)));
        when(categories.selectList(any())).thenReturn(List.of(category("four-up")));
        assertEquals("four-up", resolver.infer(List.of(entry)));
    }

    @Test
    void doesNotGuessAcrossGradesOrVolumes() {
        for (SubjectDimensions other : List.of(dimension("b", 5, 1), dimension("b", 4, 2))) {
            when(source.selectSubjectDimensions(anyList())).thenReturn(List.of(dimension("a", 4, 1), other));
            assertNull(resolver.infer(List.of(entry("a"), entry("b"))));
        }
        verifyNoInteractions(categories);
    }

    @Test
    void requiresEverySourceToHaveCompleteDimensions() {
        when(source.selectSubjectDimensions(anyList())).thenReturn(List.of(dimension("a", 4, 1)));
        assertNull(resolver.infer(List.of(entry("a"), entry("missing"))));
        SubjectDimensions partial = dimension("a", 4, 1);
        partial.setSubject(null);
        when(source.selectSubjectDimensions(anyList())).thenReturn(List.of(partial));
        assertNull(resolver.infer(List.of(entry("a"))));
        verifyNoInteractions(categories);
    }

    @Test
    void rejectsMissingOrAmbiguousPublicCategories() {
        when(source.selectSubjectDimensions(anyList())).thenReturn(List.of(dimension("a", 4, 1)));
        when(categories.selectList(any())).thenReturn(List.of(), List.of(category("a"), category("b")));
        ServiceException missing = assertThrows(ServiceException.class, () -> resolver.infer(List.of(entry("a"))));
        assertTrue(missing.getMessage().contains("缺失"));
        ServiceException ambiguous = assertThrows(ServiceException.class, () -> resolver.infer(List.of(entry("a"))));
        assertTrue(ambiguous.getMessage().contains("重复"));
    }

    @Test
    void matchesUnsplitVolumeWithoutDefaultingToUpperVolume() {
        when(source.selectSubjectDimensions(anyList())).thenReturn(List.of(dimension("a", 9, null)));
        when(categories.selectList(any())).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("volume IS NULL"));
            return List.of(category("nine"));
        });
        assertEquals("nine", resolver.infer(List.of(entry("a"))));
    }

    @Test
    void emptyOrUnclassifiedQuestionsDoNotQueryTheTaxonomy() {
        assertNull(resolver.infer(List.of()));
        assertNull(resolver.infer(List.of(entry(null))));
        verifyNoInteractions(source, categories);
    }

    private static BasketEntryVo entry(String subjectId) {
        BasketEntryVo entry = new BasketEntryVo();
        QuestionDetailVo question = new QuestionDetailVo();
        question.setSubjectId(subjectId);
        entry.setQuestion(question);
        return entry;
    }

    private static SubjectDimensions dimension(String id, int grade, Integer volume) {
        SubjectDimensions dimension = new SubjectDimensions();
        dimension.setSourceId(id);
        dimension.setSubject(1);
        dimension.setStage(grade <= 6 ? 1 : 2);
        dimension.setGrade(grade);
        dimension.setVolume(volume);
        return dimension;
    }

    private static BizPaperCategory category(String id) {
        BizPaperCategory category = new BizPaperCategory();
        category.setId(id);
        return category;
    }
}
