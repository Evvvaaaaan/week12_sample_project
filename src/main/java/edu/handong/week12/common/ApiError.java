package edu.handong.week12.common;

public record ApiError(
        String code,
        String message
) {
}
