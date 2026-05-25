package com.securityspring.core.domain.exception;

public class EmailVerificationCodeExpiredException extends RuntimeException {
    public EmailVerificationCodeExpiredException() {
        super("Código de verificação expirado");
    }
}
