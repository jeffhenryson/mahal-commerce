package com.cernecommerce.core.domain.exception.auth;

public class OAuthTokenInvalidException extends RuntimeException {
    public OAuthTokenInvalidException(String message) {
        super(message);
    }
}
