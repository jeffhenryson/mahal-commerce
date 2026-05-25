package com.securityspring.core.ports.out;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);
}
