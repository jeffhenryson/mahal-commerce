package com.cernecommerce.core.domain.model.pagamento;

/** Operadora da maquininha ou do link (PDV-F025). Só existe junto com {@link PaymentChannel}. */
public enum PaymentProvider {
    CIELO,
    INFINITYPAY
}
