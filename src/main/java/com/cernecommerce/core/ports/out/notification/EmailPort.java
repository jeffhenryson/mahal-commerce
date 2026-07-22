package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);

    void sendPasswordResetLink(String to, String username, String resetLink);

    void sendEmailChangeNotification(String oldEmail, String username, String newEmail);

    void sendPasswordChangedAlert(String to, String username);

    void sendAccountLockedAlert(String to, String username);

    void sendTotpStatusAlert(String to, String username, boolean enabled);

    void sendTokenTheftAlert(String to, String username);

    /** Status de conexão do adapter de e-mail atualmente ativo (ver crm/integracao-canal-envio, F008). */
    EmailChannelStatus channelStatus();
}
