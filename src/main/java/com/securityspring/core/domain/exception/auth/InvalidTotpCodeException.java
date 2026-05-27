package com.securityspring.core.domain.exception.auth;

public class InvalidTotpCodeException extends RuntimeException {
    public InvalidTotpCodeException() {
        super("Código TOTP inválido");
    }
}
