package com.cernecommerce.core.domain.exception.auth;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid or unknown refresh token");
    }
}
