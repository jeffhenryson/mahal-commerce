package com.securityspring.core.ports.out.notification;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);

    void sendPasswordResetLink(String to, String username, String resetLink);

    void sendEmailChangeNotification(String oldEmail, String username, String newEmail);
}
