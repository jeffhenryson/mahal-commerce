package com.securityspring.core.domain.exception;

public class EmailVerificationCodeNotFoundException extends RuntimeException {
    public EmailVerificationCodeNotFoundException() {
        super("Código de verificação inválido ou não encontrado");
    }
}
