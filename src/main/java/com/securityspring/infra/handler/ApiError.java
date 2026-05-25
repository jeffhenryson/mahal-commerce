package com.securityspring.infra.handler;

import java.time.Instant;

public record ApiError(
        String message,
        String errorCode,
        Instant timestamp,
        String path,
        String traceId
) {
    public static ApiError of(String message, String errorCode, String path, String traceId) {
        return new ApiError(message, errorCode, Instant.now(), path, traceId);
    }
}
