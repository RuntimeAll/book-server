package org.dromara.book.service.schedule;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.book.domain.entity.BizFeedbackSheet;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.mapper.BizFeedbackSheetMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.book.util.ScheduleRenderUtil;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class FeedbackSheetServiceTest {

    private final BizFeedbackSheetMapper sheets = mock(BizFeedbackSheetMapper.class);
    private final BizStudentMapper students = mock(BizStudentMapper.class);
    private final BizScheduleSessionMapper sessions = mock(BizScheduleSessionMapper.class);
    private final ScheduleRenderUtil renderer = mock(ScheduleRenderUtil.class);
    private final FeedbackSheetService service = new FeedbackSheetService(sheets, students, sessions, renderer);

    @BeforeAll
    static void configureJsonMapper() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            assertNotNull(JsonUtils.getObjectMapper());
        }
    }

    @Test
    void singlePngIncludesLessonDateAndBoundSessionTimeColumns() {
        BizFeedbackSheet sheet = new BizFeedbackSheet();
        sheet.setId(10L);
        sheet.setCreateBy(9L);
        sheet.setSessionId(20L);
        sheet.setLessonDate(LocalDate.of(2026, 9, 8));
        sheet.setTitle("课堂反馈");
        sheet.setRowsJson("[{\"seq\":\"1\",\"module\":\"计算\",\"content\":\"四则运算\","
            + "\"mastery\":\"良好\",\"weakness\":\"审题\"}]");

        BizScheduleSession session = new BizScheduleSession();
        session.setId(20L);
        session.setCreateBy(9L);
        session.setStartTime("09:05:00");
        session.setEndTime("10:40:00");

        when(sheets.selectById(10L)).thenReturn(sheet);
        when(sessions.selectByIds(anyCollection())).thenReturn(List.of(session));
        when(renderer.renderToPng(anyString(), eq("feedback_10"), eq(900), anyInt()))
            .thenReturn("feedback_10.png");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.exportPng(10L);
        }

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(renderer).renderToPng(html.capture(), eq("feedback_10"), eq(900), anyInt());
        String value = html.getValue();
        assertTrue(value.contains(">日期</th>"));
        assertTrue(value.contains(">上课时间</th>"));
        assertTrue(value.contains(">2026-09-08</td>"));
        assertTrue(value.contains(">09:05-10:40</td>"));
    }

    @Test
    void singlePngKeepsLegacyUnboundSheetExportable() {
        BizFeedbackSheet sheet = new BizFeedbackSheet();
        sheet.setId(11L);
        sheet.setCreateBy(9L);
        sheet.setLessonDate(LocalDate.of(2026, 9, 7));
        sheet.setRowsJson("[]");

        when(sheets.selectById(11L)).thenReturn(sheet);
        when(renderer.renderToPng(anyString(), eq("feedback_11"), eq(900), anyInt()))
            .thenReturn("feedback_11.png");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.exportPng(11L);
        }

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(renderer).renderToPng(html.capture(), eq("feedback_11"), eq(900), anyInt());
        String value = html.getValue();
        assertTrue(value.contains(">2026-09-07</td>"));
        assertTrue(value.contains("colspan=\"5\""));
    }

    @Test
    void planSinglePngIncludesTheLatestSheetSessionTime() {
        BizFeedbackSheet sheet = sheet(12L, 20L, LocalDate.of(2026, 9, 8));
        sheet.setPlanId(30L);
        BizScheduleSession session = session(20L, "13:30", "15:00");

        when(sheets.selectList(any())).thenReturn(List.of(sheet));
        when(sessions.selectByIds(anyCollection())).thenReturn(List.of(session));
        when(renderer.renderToPng(anyString(), eq("feedback_plan_30_single"), eq(900), anyInt()))
            .thenReturn("feedback_plan_30_single.png");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.exportPlanPng(30L, "single");
        }

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(renderer).renderToPng(html.capture(), eq("feedback_plan_30_single"), eq(900), anyInt());
        assertTrue(html.getValue().contains(">13:30-15:00</td>"));
    }

    @Test
    void planLongPngKeepsTheExistingFiveColumnLayout() {
        BizFeedbackSheet sheet = sheet(12L, 20L, LocalDate.of(2026, 9, 8));
        sheet.setPlanId(30L);

        when(sheets.selectList(any())).thenReturn(List.of(sheet));
        when(renderer.renderToPng(anyString(), eq("feedback_plan_30_long"), eq(640), anyInt()))
            .thenReturn("feedback_plan_30_long.png");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.exportPlanPng(30L, "long");
        }

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(renderer).renderToPng(html.capture(), eq("feedback_plan_30_long"), eq(640), anyInt());
        assertFalse(html.getValue().contains(">上课时间</th>"));
        verifyNoInteractions(sessions);
    }

    @Test
    void singlePngRendersAReadableImage() throws Exception {
        Path out = Paths.get("target", "feedback-render-selftest").toAbsolutePath();
        ScheduleRenderUtil realRenderer = new ScheduleRenderUtil();
        set(realRenderer, "fontMainPath", "");
        set(realRenderer, "fontHeadingPath", "");
        set(realRenderer, "artifactDir", out.toString());
        FeedbackSheetService renderService = new FeedbackSheetService(sheets, students, sessions, realRenderer);

        BizFeedbackSheet sheet = sheet(13L, 21L, LocalDate.of(2026, 9, 8));
        sheet.setRowsJson("[{\"seq\":\"1\",\"module\":\"计算\",\"content\":\"四则运算与应用题\","
            + "\"mastery\":\"良好\",\"weakness\":\"审题要再仔细一些\"},"
            + "{\"seq\":\"2\",\"module\":\"几何\",\"content\":\"平行四边形面积\","
            + "\"mastery\":\"掌握\",\"weakness\":\"注意单位换算\"}]");
        when(sheets.selectById(13L)).thenReturn(sheet);
        when(sessions.selectByIds(anyCollection())).thenReturn(List.of(session(21L, "09:05:00", "10:40:00")));

        Map<String, Object> result;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            result = renderService.exportPng(13L);
        }

        Path png = out.resolve(String.valueOf(result.get("file")));
        assertTrue(Files.exists(png) && Files.size(png) > 0);
        BufferedImage image = ImageIO.read(new File(png.toString()));
        assertNotNull(image);
        assertEquals(1800, image.getWidth());
    }

    private static BizFeedbackSheet sheet(Long id, Long sessionId, LocalDate lessonDate) {
        BizFeedbackSheet sheet = new BizFeedbackSheet();
        sheet.setId(id);
        sheet.setCreateBy(9L);
        sheet.setSessionId(sessionId);
        sheet.setLessonDate(lessonDate);
        sheet.setTitle("课堂反馈");
        sheet.setRowsJson("[{\"seq\":\"1\",\"module\":\"计算\",\"content\":\"四则运算\","
            + "\"mastery\":\"良好\",\"weakness\":\"审题\"}]");
        return sheet;
    }

    private static BizScheduleSession session(Long id, String startTime, String endTime) {
        BizScheduleSession session = new BizScheduleSession();
        session.setId(id);
        session.setCreateBy(9L);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        return session;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field declaredField = target.getClass().getDeclaredField(field);
        declaredField.setAccessible(true);
        declaredField.set(target, value);
    }
}
