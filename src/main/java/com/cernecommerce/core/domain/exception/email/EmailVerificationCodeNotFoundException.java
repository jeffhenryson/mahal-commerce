package com.cernecommerce.core.domain.exception.email;

public class EmailVerificationCodeNotFoundException extends RuntimeException {
    public EmailVerificationCodeNotFoundException() {
        super("Verification code not found or already used");
    }
}
