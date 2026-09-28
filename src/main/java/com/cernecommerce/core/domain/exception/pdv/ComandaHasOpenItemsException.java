package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F023 — {@code finish} numa mesa que ainda tem linha a cobrar. Encerrar sem cobrar é
 * {@code close}; encerrar sem cobrança nenhuma é cancelar. 409.
 */
public class ComandaHasOpenItemsException extends RuntimeException {
    public ComandaHasOpenItemsException(Long comandaId) {
        super("A mesa " + comandaId + " ainda tem itens em aberto — cobre-os antes de encerrar");
    }
}
