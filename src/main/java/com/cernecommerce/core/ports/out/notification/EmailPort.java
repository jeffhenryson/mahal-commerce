package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;

import java.math.BigDecimal;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);

    void sendPasswordResetLink(String to, String username, String resetLink);

    void sendEmailChangeNotification(String oldEmail, String username, String newEmail);

    void sendPasswordChangedAlert(String to, String username);

    void sendAccountLockedAlert(String to, String username);

    void sendTotpStatusAlert(String to, String username, boolean enabled);

    void sendTokenTheftAlert(String to, String username);

    /** Confirmação de pedido criado, disparada no checkout (marketplace). */
    void sendOrderConfirmation(String to, String customerName, String orderReference, BigDecimal total,
            int itemCount, String checkoutUrl);

    /** Aviso de mudança de status do pedido (pagamento confirmado, separado, enviado, entregue). */
    void sendOrderStatusUpdate(String to, String customerName, String orderReference, String newStatusLabel);

    /** Aviso de pedido cancelado ou reembolsado — {@code refunded} muda o texto entre os dois casos. */
    void sendOrderCancellation(String to, String customerName, String orderReference, String reason,
            boolean refunded);

    /** Status de conexão do adapter de e-mail atualmente ativo (ver crm/integracao-canal-envio, F008). */
    EmailChannelStatus channelStatus();
}
