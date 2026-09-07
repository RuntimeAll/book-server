package org.dromara.book.controller;

import jakarta.validation.ConstraintViolationException;
import org.dromara.book.domain.bo.ShelfItemPageBo;
import org.dromara.book.service.IPaperDetailService;
import org.dromara.book.service.IPaperLibraryService;
import org.dromara.book.service.IPaperSourceService;
import org.dromara.book.service.IQuestionService;
import org.dromara.book.service.QuestionBatchService;
import org.dromara.book.service.paper.QuestionBasketEntryService;
import org.dromara.book.service.shelf.BookExportService;
import org.dromara.book.service.shelf.ShelfPdfImportService;
import org.dromara.book.service.shelf.ShelfService;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class MaterialValidationAdviceTest {
    private final ShelfService shelf = mock(ShelfService.class);
    private final IQuestionService questions = mock(IQuestionService.class);
    private final QuestionBatchService batch = mock(QuestionBatchService.class);
    private final IPaperLibraryService papers = mock(IPaperLibraryService.class);
    private final IPaperSourceService source = mock(IPaperSourceService.class);
    private final QuestionBasketEntryService basket = mock(QuestionBasketEntryService.class);
    private final GlobalExceptionHandler global = spy(new GlobalExceptionHandler());
    private final MaterialValidationAdvice advice = new MaterialValidationAdvice();
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                new ShelfController(shelf, mock(BookExportService.class), mock(ShelfPdfImportService.class)),
                new QuestionController(questions, batch),
                new PaperLibraryController(papers, mock(IPaperDetailService.class)),
                new QuestionBasketEntryController(basket),
                new PaperSourceController(source))
            .setControllerAdvice(advice, global, new MisiktEnvelopeAdvice())
            .setValidator(validator)
            .build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    @Test
    void actualShelfModelBindingRejectsPaginationOverflowWith400() throws Exception {
        mvc.perform(get("/teacher/shelf/book/1/node/2/items").param("pageSize", "101"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/teacher/shelf/book/1/node/2/items").param("pageNum", "0"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(shelf);
    }

    @Test
    void actualQuestionBodyValidationRejectsInvalidIdWith400() throws Exception {
        mvc.perform(post("/teacher/question/batch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[0]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(batch);
    }

    @Test
    void actualPaperDeleteValidationRejectsMissingInvalidAndFractionalIds() throws Exception {
        for (String body : List.of("{}", "{\"paperId\":0}", "{\"paperId\":-1}", "{\"paperId\":\"abc\"}",
            "{\"paperId\":1.5}", "{\"paperId\":\"9223372036854775808\"}", "{\"paperId\":[]}")) {
            mvc.perform(post("/teacher/exam/paper/delete").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(papers);
    }

    @Test
    void actualPathAndBasketParameterTypeMismatchReturn400() throws Exception {
        mvc.perform(get("/teacher/shelf/book/not-a-long/outline"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/teacher/question/basket/entries").param("pageIndex", "not-an-int"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(shelf, basket);
    }

    @Test
    void constraintViolationIs400OnlyForScopedController() throws Exception {
        ShelfItemPageBo invalid = new ShelfItemPageBo();
        invalid.setPageSize(101);
        ConstraintViolationException exception = new ConstraintViolationException(validator.validate(invalid));
        when(shelf.getOutline(1L)).thenThrow(exception);
        mvc.perform(get("/teacher/shelf/book/1/outline"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));

        // The existing global policy is deliberately unchanged for an out-of-scope controller.
        when(source.getPaperSource(1L)).thenThrow(exception);
        mvc.perform(get("/teacher/paper/source/1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        verify(global).constraintViolationException(exception);
    }

    @Test
    void handlerMethodInputValidationIs400ButInvalidServerReturnRemains500() throws Exception {
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
        when(exception.getAllErrors()).thenAnswer(unused -> List.of(new DefaultMessageSourceResolvable("invalid parameter")));
        when(shelf.getOutline(1L)).thenThrow(exception);
        mvc.perform(get("/teacher/shelf/book/1/outline"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        when(exception.isForReturnValue()).thenReturn(true);
        mvc.perform(get("/teacher/shelf/book/1/outline"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void unknownRuntimeFailureStillReachesGlobal500Handler() throws Exception {
        IllegalStateException failure = new IllegalStateException("database failed");
        when(shelf.getOutline(1L)).thenThrow(failure);
        mvc.perform(get("/teacher/shelf/book/1/outline"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        verify(global).handleRuntimeException(any(IllegalStateException.class), any());
    }

    @Test
    void bindingAndBaseTypeMismatchHandlersUseBadRequestWithoutRejectedValues() {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new ShelfItemPageBo(), "page");
        errors.rejectValue("pageSize", "Max", "每页最多100条");
        var result = advice.handleBinding(new BindException(errors));
        assertEquals(400, result.getStatusCode().value());
        assertEquals(400, result.getBody().getCode());
        assertEquals("每页最多100条", result.getBody().getMsg());

        var mismatch = advice.handleTypeMismatch(new TypeMismatchException("private rejected value", Long.class));
        assertEquals(400, mismatch.getStatusCode().value());
        assertEquals("请求参数类型不正确", mismatch.getBody().getMsg());
    }
}
