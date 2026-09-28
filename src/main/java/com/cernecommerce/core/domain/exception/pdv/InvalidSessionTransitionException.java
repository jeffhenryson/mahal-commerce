package com.cernecommerce.core.domain.exception.pdv;

import com.cernecommerce.core.domain.model.pdv.SessionStatus;

/** PDV-F023 — status de sessão pedido fora da ordem NA_FILA → PREPARANDO → ENTREGUE → RECOLHIDO. 409. */
public class InvalidSessionTransitionException extends RuntimeException {
    public InvalidSessionTransitionException(Long itemId, SessionStatus from, SessionStatus to) {
        super("A sessão " + itemId + " não pode ir de " + from + " para " + to);
    }
}
