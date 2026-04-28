package com.yclaims.kernel.exception;

/**
 * Thrown when an authenticated user attempts an action they are not permitted to perform.
 * Maps to HTTP 403 Forbidden.
 */
public class UnauthorisedException extends RuntimeException {

    public UnauthorisedException(String message) {
        super(message);
    }
}
