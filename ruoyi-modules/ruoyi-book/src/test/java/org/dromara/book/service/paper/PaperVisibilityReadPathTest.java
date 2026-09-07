package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.dromara.book.domain.bo.PaperPageBo;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.book.mapper.BizPaperCategoryMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizPaperQuestionMapper;
import org.dromara.book.mapper.BizPaperSectionMapper;
import org.dromara.book.service.IQuestionService;
import org.dromara.book.service.impl.PaperDetailServiceImpl;
import org.dromara.book.service.impl.PaperLibraryServiceImpl;
import org.dromara.book.service.impl.PaperSourceServiceImpl;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperVisibilityReadPathTest {
    @BeforeAll
    static void initializeMetadata() {
        PaperTestMetadata.initialize();
    }

    @Test
    void publicPageShowsDraftOnlyToSuperAdminAndMineKeepsDraft() {
        BizPaperMapper papers = mock(BizPaperMapper.class);
        PaperLibraryServiceImpl service = new PaperLibraryServiceImpl(
            mock(BizPaperCategoryMapper.class), papers, mock(BizPaperSectionMapper.class),
            mock(BizPaperQuestionMapper.class), mock(PaperCompositionService.class));
        when(papers.selectPaperListPage(any(), any())).thenAnswer(invocation -> {
            IPage<PaperListItemVo> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        PaperPageBo publicScope = new PaperPageBo();
        publicScope.setScope("public");
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_ID);
            login.when(() -> LoginHelper.isSuperAdmin(SystemConstants.SUPER_ADMIN_ID)).thenReturn(true);
            service.page(publicScope);

            login.when(LoginHelper::getUserId).thenReturn(9L);
            login.when(() -> LoginHelper.isSuperAdmin(9L)).thenReturn(false);
            service.page(publicScope);

            PaperPageBo mine = new PaperPageBo();
            mine.setScope("mine");
            service.page(mine);
        }
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Wrapper<PaperListItemVo>> wrapper =
            org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(papers, org.mockito.Mockito.times(3))
            .selectPaperListPage(any(), wrapper.capture());
        assertTrue(wrapper.getAllValues().get(0).getCustomSqlSegment().contains("p.status IN"));
        assertTrue(wrapper.getAllValues().get(1).getCustomSqlSegment().contains("p.status ="));
        assertFalse(wrapper.getAllValues().get(1).getCustomSqlSegment().contains("p.status IN"));
        assertTrue(wrapper.getAllValues().get(2).getCustomSqlSegment().contains("p.status IN"));
    }

    @Test
    void listFieldsExposePublishedAndVisibilityCapabilitySeparately() {
        BizPaperMapper papers = mock(BizPaperMapper.class);
        PaperLibraryServiceImpl service = new PaperLibraryServiceImpl(
            mock(BizPaperCategoryMapper.class), papers, mock(BizPaperSectionMapper.class),
            mock(BizPaperQuestionMapper.class), mock(PaperCompositionService.class));
        PaperListItemVo item = new PaperListItemVo();
        item.setCreateUser(SystemConstants.SUPER_ADMIN_ID);
        item.setPaperKind("1");
        item.setStatus(0);
        when(papers.selectPaperListPage(any(), any())).thenAnswer(invocation -> {
            IPage<PaperListItemVo> page = invocation.getArgument(0);
            page.setRecords(List.of(item));
            return page;
        });
        PaperPageBo bo = new PaperPageBo();
        bo.setScope("public");
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_ID);
            service.page(bo);
        }
        assertFalse(item.isPublished());
        assertTrue(item.isCanChangeVisibility());

        item.setPaperKind("2");
        assertFalse(SelectionRules.canChangeVisibility(SelectionRules.OFFICIAL_OWNER_ID,
            item.getPaperKind(), SystemConstants.SUPER_ADMIN_ID));
    }

    @Test
    void ownerCanReadUnpublishedDetailButOtherUserCannotLoadRowsOrSource() {
        BizPaperMapper papers = mock(BizPaperMapper.class);
        BizPaperQuestionMapper rows = mock(BizPaperQuestionMapper.class);
        PaperDetailServiceImpl details = new PaperDetailServiceImpl(
            papers, rows, mock(IQuestionService.class), new QuestionSnapshotCodec(new com.fasterxml.jackson.databind.ObjectMapper()));
        PaperDetailVo header = new PaperDetailVo();
        header.setPaperId(10L);
        header.setCreateBy("9");
        when(papers.selectPaperDetailHeader(10L, "9")).thenReturn(header);
        when(papers.selectPaperDetailHeader(10L, "8")).thenReturn(null);
        when(papers.selectSectionsByPaperId(10L)).thenReturn(List.of());
        when(rows.selectList(any())).thenReturn(List.of());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertTrue(details.getPaperDetail(10L) != null);
            assertTrue(new PaperSourceServiceImpl(details).getPaperSource(10L) != null);
            login.when(LoginHelper::getUserId).thenReturn(8L);
            assertNull(details.getPaperDetail(10L));
            assertNull(new PaperSourceServiceImpl(details).getPaperSource(10L));
        }
        org.mockito.Mockito.verify(rows, org.mockito.Mockito.times(2)).selectList(any());
    }
}
