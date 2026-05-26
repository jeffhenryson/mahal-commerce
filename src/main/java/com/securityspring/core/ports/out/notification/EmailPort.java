package com.securityspring.core.ports.out.notification;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);
}
