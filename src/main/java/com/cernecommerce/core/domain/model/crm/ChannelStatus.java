package com.cernecommerce.core.domain.model.crm;

/**
 * Status de conexão de um canal de envio (WhatsApp/E-mail), consumido pelo badge de status
 * na tela Automações. {@code conectado} reflete a configuração real do backend (qual adapter
 * está ativo por profile) — não é um health-check de rede ao vivo.
 */
public record ChannelStatus(ChannelType canal, boolean conectado, String provedor, String detalhe) {

    public ChannelStatus {
        if (canal == null) {
            throw new IllegalArgumentException("canal é obrigatório");
        }
    }

    public static ChannelStatus of(ChannelType canal, boolean conectado, String provedor, String detalhe) {
        return new ChannelStatus(canal, conectado, provedor, detalhe);
    }
}
