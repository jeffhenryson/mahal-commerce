package com.securityspring.core.domain.exception.email;

public class EmailVerificationCodeExpiredException extends RuntimeException {
    public EmailVerificationCodeExpiredException() {
        super("Verification code has expired");
    }
}
