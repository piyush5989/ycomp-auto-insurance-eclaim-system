package com.yclaims.kernel.web;

import java.time.Instant;
import java.util.Map;

/**
 * Consistent API response envelope used by every endpoint — success or error.
 *
 * Success:  { data: <T>, error: null,     meta: { correlationId, timestamp, version } }
 * Error:    { data: null, error: { code, message, fieldErrors }, meta: { ... } }
 */
public record ApiResponse<T>(
        T data,
        ApiError error,
        ApiMeta meta
) {

    public record ApiError(
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {}

    public record ApiMeta(
            String correlationId,
            Instant timestamp,
            String version
    ) {}

    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(data, null, meta(correlationId));
    }

    public static <T> ApiResponse<T> error(String code, String message, String correlationId) {
        return new ApiResponse<>(null, new ApiError(code, message, null), meta(correlationId));
    }

    public static <T> ApiResponse<T> validationError(String message,
                                                      Map<String, String> fieldErrors,
                                                      String correlationId) {
        return new ApiResponse<>(null,
                new ApiError("VALIDATION_ERROR", message, fieldErrors),
                meta(correlationId));
    }

    private static ApiMeta meta(String correlationId) {
        return new ApiMeta(correlationId, Instant.now(), "v1");
    }
}
