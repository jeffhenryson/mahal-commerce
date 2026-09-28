package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F025 — canal/operadora incoerente com o pagamento: DINHEIRO não passa por maquininha nem link,
 * e operadora sem canal não diz por onde a cobrança saiu. 400.
 */
public class InvalidPaymentChannelException extends RuntimeException {
    public InvalidPaymentChannelException(String message) {
        super(message);
    }
}
