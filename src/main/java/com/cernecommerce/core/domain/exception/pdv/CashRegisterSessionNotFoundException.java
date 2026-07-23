package com.cernecommerce.core.domain.exception.pdv;

public class CashRegisterSessionNotFoundException extends RuntimeException {
    public CashRegisterSessionNotFoundException(Long sessionId) {
        super("Sessão de caixa não encontrada: " + sessionId);
    }
}
