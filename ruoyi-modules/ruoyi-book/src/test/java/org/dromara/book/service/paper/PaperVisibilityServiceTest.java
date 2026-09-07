package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.mapper.BizPaperSectionMapper;
import org.dromara.book.service.impl.PaperLibraryServiceImpl;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperVisibilityServiceTest {
    private final BizPaperMapper papers = mock(BizPaperMapper.class);
    private final PaperLibraryServiceImpl service = new PaperLibraryServiceImpl(
        mock(BizPaperCategoryMapper.class), papers, mock(BizPaperSectionMapper.class),
        mock(BizPaperQuestionMapper.class), mock(PaperCompositionService.class));

    @BeforeAll
    static void initializeMetadata() {
        PaperTestMetadata.initialize();
    }

    @Test
    void ordinaryTeacherCannotChangeVisibility() {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertCode(403, () -> service.changeVisibility(10L, false));
        }
        verifyNoInteractions(papers);
    }

    @Test
    void superAdminChangesOnlyOfficialOrdinaryPaperAndPersistsStatus() {
        BizPaper paper = paper(SelectionRules.OFFICIAL_OWNER_ID, "1", BizPaper.STATUS_DRAFT);
        when(papers.lockById(10L)).thenReturn(paper);
        when(papers.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_ID);
            login.when(() -> LoginHelper.isSuperAdmin(SystemConstants.SUPER_ADMIN_ID)).thenReturn(true);
            service.changeVisibility(10L, true);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<BizPaper>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(papers).update(isNull(), update.capture());
        assertTrue(update.getValue().getParamNameValuePairs().containsValue(BizPaper.STATUS_PUBLISHED));
        assertFalse(update.getValue().getParamNameValuePairs().containsValue(BizPaper.STATUS_DELETED));
    }

    @Test
    void unrelatedPaperIsRejectedWithoutUpdate() {
        BizPaper privatePaper = paper("99", "1", BizPaper.STATUS_DRAFT);
        when(papers.lockById(10L)).thenReturn(privatePaper);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_ID);
            login.when(() -> LoginHelper.isSuperAdmin(SystemConstants.SUPER_ADMIN_ID)).thenReturn(true);
            assertCode(403, () -> service.changeVisibility(10L, true));
            privatePaper.setCreateBy(SelectionRules.OFFICIAL_OWNER_ID);
            privatePaper.setPaperKind("2");
            assertCode(403, () -> service.changeVisibility(10L, true));
        }
        verify(papers, never()).update(any(), any());
    }

    @Test
    void visibilitySwitchIsIdempotentForCurrentStatus() {
        BizPaper paper = paper(SelectionRules.OFFICIAL_OWNER_ID, "1", BizPaper.STATUS_PUBLISHED);
        when(papers.lockById(10L)).thenReturn(paper);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_ID);
            login.when(() -> LoginHelper.isSuperAdmin(SystemConstants.SUPER_ADMIN_ID)).thenReturn(true);
            service.changeVisibility(10L, true);
        }
        verify(papers, never()).update(any(), any());
    }

    private BizPaper paper(String owner, String kind, String status) {
        BizPaper paper = new BizPaper();
        paper.setId(10L);
        paper.setCreateBy(owner);
        paper.setPaperKind(kind);
        paper.setStatus(status);
        return paper;
    }

    private void assertCode(int expected, org.junit.jupiter.api.function.Executable action) {
        ServiceException exception = assertThrows(ServiceException.class, action);
        assertEquals(expected, exception.getCode());
    }
}
