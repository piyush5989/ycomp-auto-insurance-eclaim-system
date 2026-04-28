package com.yclaims.kernel.exception;

/**
 * Base for all domain rule violations.
 * Maps to HTTP 400 Bad Request in GlobalExceptionHandler.
 */
public class DomainException extends RuntimeException {

    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
