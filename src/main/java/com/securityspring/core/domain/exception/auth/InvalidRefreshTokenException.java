package com.securityspring.core.domain.exception.auth;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid or unknown refresh token");
    }
}
