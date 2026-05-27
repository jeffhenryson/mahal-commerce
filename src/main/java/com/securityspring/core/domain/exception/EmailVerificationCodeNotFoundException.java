package com.securityspring.core.domain.exception;

public class EmailVerificationCodeNotFoundException extends RuntimeException {
    public EmailVerificationCodeNotFoundException() {
        super("Verification code not found or already used");
    }
}
