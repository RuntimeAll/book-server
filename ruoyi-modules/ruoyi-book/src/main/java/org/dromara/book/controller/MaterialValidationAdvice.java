package org.dromara.book.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.dromara.common.core.domain.R;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Request validation for the lecture/basket/paper flow only. Business and unknown exceptions
 * remain with the existing global handler; this is not a replacement global exception policy.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
    QuestionController.class, ShelfController.class, PaperLibraryController.class, QuestionBasketEntryController.class
})
public class MaterialValidationAdvice {
    /** MethodArgumentNotValidException also extends BindException. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBinding(BindException exception) {
        return badRequest(messages(exception.getAllErrors().stream()
            .map(MessageSourceResolvable::getDefaultMessage)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraints(ConstraintViolationException exception) {
        return badRequest(messages(exception.getConstraintViolations().stream().map(ConstraintViolation::getMessage)));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<R<Void>> handleMethodValidation(HandlerMethodValidationException exception) {
        // A controller producing an invalid response is a server defect, not bad client input.
        if (exception.isForReturnValue()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(R.fail(500, "响应数据校验失败"));
        }
        return badRequest(messages(exception.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage)));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(TypeMismatchException exception) {
        return badRequest("请求参数类型不正确");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return badRequest("请求体格式或字段类型不正确");
    }

    private ResponseEntity<R<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(R.fail(400, message));
    }

    private String messages(Stream<String> messages) {
        String message = messages.filter(Objects::nonNull).filter(value -> !value.isBlank())
            .distinct().limit(5).collect(Collectors.joining("; "));
        return message.isEmpty() ? "请求参数校验失败" : message;
    }
}
