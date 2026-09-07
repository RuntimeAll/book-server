package org.dromara.book.service.paper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.book.domain.bo.ExportPaperBo;
import org.dromara.book.domain.entity.BizExportRecord;
import org.dromara.book.domain.vo.PaperDetailVo;
import org.dromara.book.mapper.BizExportRecordMapper;
import org.dromara.book.mapper.BizPaperMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.service.ExportPdfWorker;
import org.dromara.book.service.PdfComposer;
import org.dromara.book.service.WatermarkTextResolver;
import org.dromara.book.service.impl.ExportRecordServiceImpl;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperVisibilityExportTest {
    @BeforeAll
    static void initializeMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "export-visibility-test");
        assistant.setCurrentNamespace(BizExportRecord.class.getName());
        TableInfoHelper.initTableInfo(assistant, BizExportRecord.class);
    }

    private final BizExportRecordMapper records = mock(BizExportRecordMapper.class);
    private final BizPaperMapper papers = mock(BizPaperMapper.class);
    private final ExportPdfWorker worker = mock(ExportPdfWorker.class);
    private final ExportRecordServiceImpl service = new ExportRecordServiceImpl(records, papers, worker, new ObjectMapper());

    private ExportPaperBo request(String paperId) {
        ExportPaperBo request = new ExportPaperBo();
        request.setPaperId(paperId);
        request.setIds(List.of("99"));
        request.setFileName("visibility test");
        return request;
    }

    @Test
    void hiddenPaperCannotCreateExportTask() {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertEquals(403, assertThrows(ServiceException.class, () -> service.submit(request("10"))).getCode());
        }
        verify(papers).selectPaperDetailHeader(10L, "9");
        verify(records, never()).insert(any(BizExportRecord.class));
        verifyNoInteractions(worker);
    }

    @Test
    void invalidPaperIdCannotBecomeAnUnboundExport() {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertEquals(400, assertThrows(ServiceException.class, () -> service.submit(request("invalid"))).getCode());
        }
        verify(records, never()).insert(any(BizExportRecord.class));
        verifyNoInteractions(worker);
    }

    @Test
    void hiddenPaperCannotRetryAnOldTask() {
        BizExportRecord old = new BizExportRecord();
        old.setUserId(9L);
        old.setPaperId(10L);
        when(records.selectById(20L)).thenReturn(old);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            assertEquals(403, assertThrows(ServiceException.class, () -> service.retry(20L)).getCode());
        }
        verify(records, never()).insert(any(BizExportRecord.class));
        verifyNoInteractions(worker);
    }

    @Test
    void ownerReadablePaperCanStillSubmit() {
        when(papers.selectPaperDetailHeader(10L, "1")).thenReturn(new PaperDetailVo());
        when(records.insert(any(BizExportRecord.class))).thenAnswer(invocation -> {
            invocation.<BizExportRecord>getArgument(0).setId(20L);
            return 1;
        });
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);
            service.submit(request("10"));
        }
        verify(worker).run(20L);
    }

    @Test
    void queuedTaskRechecksVisibilityBeforeReadingQuestions() {
        BizExportRecord queued = new BizExportRecord();
        queued.setId(20L);
        queued.setUserId(9L);
        queued.setPaperId(10L);
        when(records.selectById(20L)).thenReturn(queued);
        BizQuestionMapper questions = mock(BizQuestionMapper.class);
        PdfComposer composer = mock(PdfComposer.class);
        ExportPdfWorker actualWorker = new ExportPdfWorker(records, papers, questions, composer,
            new ObjectMapper(), mock(WatermarkTextResolver.class));
        actualWorker.run(20L);
        verify(papers).selectPaperDetailHeader(10L, "9");
        ArgumentCaptor<BizExportRecord> update = ArgumentCaptor.forClass(BizExportRecord.class);
        verify(records).updateById(update.capture());
        assertEquals(BizExportRecord.STATUS_FAILED, update.getValue().getStatus());
        assertEquals("来源试卷不存在或已无权访问", update.getValue().getErrorMsg());
        verifyNoInteractions(questions, composer);
    }
}
