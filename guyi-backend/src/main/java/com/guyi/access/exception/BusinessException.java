package com.guyi.access.exception;

/**
 * Thrown for expected, user-facing business rule violations (e.g. "卡密不存在").
 * The GlobalExceptionHandler returns {@link #getMessage()} directly to the client,
 * unlike generic RuntimeExceptions which are masked with a generic message.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
