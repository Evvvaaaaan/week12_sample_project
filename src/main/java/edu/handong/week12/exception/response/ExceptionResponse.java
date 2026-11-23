package edu.handong.week12.exception.response;

import edu.handong.week12.exception.constants.ExceptionCode;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ExceptionResponse(
        Instant timestamp,
        HttpStatus httpStatus,
        String code,
        String message
) {

    public static ExceptionResponse of(ExceptionCode exceptionCode) {
        return of(exceptionCode, exceptionCode.getMessage());
    }

    public static ExceptionResponse of(ExceptionCode exceptionCode, String message) {
        return new ExceptionResponse(
                Instant.now(),
                exceptionCode.getHttpStatus(),
                exceptionCode.name(),
                message
        );
    }
}
