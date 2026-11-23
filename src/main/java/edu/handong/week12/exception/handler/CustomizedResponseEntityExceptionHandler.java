package edu.handong.week12.exception.handler;

import edu.handong.week12.exception.CustomException;
import edu.handong.week12.exception.constants.ExceptionCode;
import edu.handong.week12.exception.response.ExceptionResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@RestControllerAdvice
public class CustomizedResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    // @Valid 검증 실패 (빈 제목/저자, 음수 가격) → 400
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return toResponse(ExceptionResponse.of(ExceptionCode.VALIDATION_FAILED, message));
    }

    // JSON 문법 오류, 타입 불일치 (예: price에 문자열) → 400
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return toResponse(ExceptionResponse.of(ExceptionCode.INVALID_JSON));
    }

    // 경로 변수 타입 불일치 (예: /api/books/abc) → 400
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return toResponse(ExceptionResponse.of(ExceptionCode.INVALID_PARAMETER));
    }

    // 존재하지 않는 필드로 정렬 (예: sort=foo,desc) → 400
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Object> handlePropertyReference(PropertyReferenceException ex) {
        return toResponse(ExceptionResponse.of(ExceptionCode.INVALID_PARAMETER));
    }

    // 없는 ID로 조회/수정/삭제 → 404
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleCustomException(CustomException ex) {
        return toResponse(ExceptionResponse.of(ex.getExceptionCode()));
    }

    // 그 외 예상하지 못한 예외 → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllException(Exception ex) {
        logger.error("Unexpected error", ex);
        return toResponse(ExceptionResponse.of(ExceptionCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<Object> toResponse(ExceptionResponse response) {
        return ResponseEntity.status(response.httpStatus()).body(response);
    }
}
