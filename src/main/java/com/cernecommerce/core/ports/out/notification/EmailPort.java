package com.cernecommerce.core.ports.out.notification;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);

    void sendPasswordResetLink(String to, String username, String resetLink);

    void sendEmailChangeNotification(String oldEmail, String username, String newEmail);

    void sendPasswordChangedAlert(String to, String username);

    void sendAccountLockedAlert(String to, String username);

    void sendTotpStatusAlert(String to, String username, boolean enabled);

    void sendTokenTheftAlert(String to, String username);
}
