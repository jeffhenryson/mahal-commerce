package com.cernecommerce.core.domain.exception.auth;

public class PasswordResetTokenExpiredException extends RuntimeException {
    public PasswordResetTokenExpiredException() {
        super("Token de recuperação de senha expirado ou já utilizado");
    }
}
