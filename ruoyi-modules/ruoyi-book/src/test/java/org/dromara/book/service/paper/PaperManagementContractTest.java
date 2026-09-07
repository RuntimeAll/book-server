package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperManagementContractTest {
    @Test
    void officialPaperRequiresSuperAdminAndPrivatePaperRequiresItsOwner() {
        String official = SelectionRules.OFFICIAL_OWNER_ID;
        assertTrue(SelectionRules.canManagePaper(official, SystemConstants.SUPER_ADMIN_ID));
        assertFalse(SelectionRules.canManagePaper(official, null));
        assertFalse(SelectionRules.canManagePaper(official, 99L));
        assertTrue(SelectionRules.canManagePaper("99", 99L));
        assertFalse(SelectionRules.canManagePaper("99", 98L));
        assertFalse(SelectionRules.canManagePaper("99", SystemConstants.SUPER_ADMIN_ID));
        assertFalse(SelectionRules.canManagePaper(null, 99L));
    }

    @Test
    void publicDetailReportsPermissionForTheActualCaller() {
        BizPaperMapper papers = mock(BizPaperMapper.class);
        BizPaperQuestionMapper rows = mock(BizPaperQuestionMapper.class);
        var service = new PaperDetailServiceImpl(papers, rows, mock(IQuestionService.class),
            new QuestionSnapshotCodec(new ObjectMapper()));
        PaperTestMetadata.initialize();
        when(papers.selectSectionsByPaperId(10L)).thenReturn(List.of());
        when(rows.selectList(any())).thenReturn(List.of());
        for (Long caller : new Long[]{SystemConstants.SUPER_ADMIN_ID, 99L, null}) {
            PaperDetailVo header = new PaperDetailVo();
            header.setPaperId(10L);
            header.setCreateBy(SelectionRules.OFFICIAL_OWNER_ID);
            when(papers.selectPaperDetailHeader(10L, caller == null ? null : caller.toString())).thenReturn(header);
            try (var login = mockStatic(LoginHelper.class)) {
                login.when(LoginHelper::getUserId).thenReturn(caller);
                assertEquals(SystemConstants.SUPER_ADMIN_ID.equals(caller), service.getPaperDetail(10L).isCanManage());
            }
        }
    }

    @Test
    void publicPageUsesNewestFirstAndReturnsManagementCapability() {
        BizPaperMapper papers = mock(BizPaperMapper.class);
        var service = new PaperLibraryServiceImpl(mock(BizPaperCategoryMapper.class), papers,
            mock(BizPaperSectionMapper.class), mock(BizPaperQuestionMapper.class), mock(PaperCompositionService.class));
        PaperListItemVo item = new PaperListItemVo();
        item.setCreateUser(SystemConstants.SUPER_ADMIN_ID);
        when(papers.selectPaperListPage(any(), any())).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(1);
            String sql = query.getCustomSqlSegment();
            assertTrue(sql.contains("p.create_time DESC,p.id DESC"), sql);
            assertFalse(sql.contains("p.subject_id"), sql);
            assertTrue(sql.contains("p.create_by"), sql);
            IPage<PaperListItemVo> page = invocation.getArgument(0);
            page.setRecords(List.of(item));
            page.setTotal(1);
            return page;
        });
        for (Long caller : new Long[]{SystemConstants.SUPER_ADMIN_ID, 99L, null}) {
            try (var login = mockStatic(LoginHelper.class)) {
                login.when(LoginHelper::getUserId).thenReturn(caller);
                PaperPageBo bo = new PaperPageBo();
                bo.setScope("public");
                service.page(bo);
                assertEquals(SystemConstants.SUPER_ADMIN_ID.equals(caller), item.isCanManage());
            }
        }
    }

    @Test
    void creatorIdsSerializeAsStringsForSmallAndSnowflakeValues() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (Long id : new Long[]{1L, 2099999999999999999L}) {
            PaperListItemVo item = new PaperListItemVo();
            item.setCreateUser(id);
            item.setCanManage(true);
            var node = json.readTree(json.writeValueAsString(item));
            assertTrue(node.get("createUser").isTextual());
            assertEquals(id.toString(), node.get("createUser").asText());
            assertTrue(node.get("canManage").asBoolean());
        }
    }
}
