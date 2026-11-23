package edu.handong.week12.exception.constants;

import org.springframework.http.HttpStatus;

public enum ExceptionCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "요청 JSON 형식이 올바르지 않습니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터 형식이 올바르지 않습니다."),
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "책이 존재하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 에러가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ExceptionCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
