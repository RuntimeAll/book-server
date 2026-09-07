package org.dromara.book.service.paper;

import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.mapper.BizPaperSectionMapper;
import org.dromara.book.service.impl.PaperLibraryServiceImpl;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperLibraryBoundaryTest {
    private final BizPaperMapper papers = mock(BizPaperMapper.class);
    private final BizPaperQuestionMapper questions = mock(BizPaperQuestionMapper.class);
    private final BizPaperSectionMapper sections = mock(BizPaperSectionMapper.class);
    private final PaperLibraryServiceImpl service = new PaperLibraryServiceImpl(
        mock(BizPaperCategoryMapper.class), papers, sections, questions, mock(PaperCompositionService.class));

    @Test
    void anonymousDeleteAndMineReturn401BeforeMapperAccess() {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(null);
            assertCode(401, () -> service.deleteExamPaper(1L));
            PaperPageBo bo = new PaperPageBo();
            bo.setScope("mine");
            assertCode(401, () -> service.page(bo));
        }
        verifyNoInteractions(papers, questions, sections);
    }

    @Test
    void missingOrNonPositiveIdReturns400BeforeMapperAccess() {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertCode(400, () -> service.deleteExamPaper(null));
            assertCode(400, () -> service.deleteExamPaper(0L));
            assertCode(400, () -> service.deleteExamPaper(-1L));
        }
        verifyNoInteractions(papers, questions, sections);
    }

    @Test
    void absentPaperReturns404WithoutDeletingRelations() {
        when(papers.lockById(1L)).thenReturn(null);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertCode(404, () -> service.deleteExamPaper(1L));
        }
        verifyNoInteractions(questions, sections);
    }

    @Test
    void privateAndOfficialOtherOwnerDeleteReturns403WithoutDeletingRelations() {
        BizPaper paper = new BizPaper();
        paper.setId(1L);
        paper.setCreateBy("99");
        when(papers.lockById(1L)).thenReturn(paper);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertCode(403, () -> service.deleteExamPaper(1L));
            paper.setCreateBy(SelectionRules.OFFICIAL_OWNER_ID);
            assertCode(403, () -> service.deleteExamPaper(1L));
        }
        verifyNoInteractions(questions, sections);
    }

    private void assertCode(int expected, org.junit.jupiter.api.function.Executable action) {
        ServiceException exception = assertThrows(ServiceException.class, action);
        assertEquals(expected, exception.getCode());
    }
}
