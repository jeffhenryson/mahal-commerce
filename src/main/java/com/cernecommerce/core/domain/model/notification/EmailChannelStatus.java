package com.cernecommerce.core.domain.model.notification;

/** Status de conexão do adapter de e-mail ativo (ver {@link com.cernecommerce.core.ports.out.notification.EmailPort}). */
public record EmailChannelStatus(boolean conectado, String provedor, String detalhe) {

    public static EmailChannelStatus of(boolean conectado, String provedor, String detalhe) {
        return new EmailChannelStatus(conectado, provedor, detalhe);
    }
}
