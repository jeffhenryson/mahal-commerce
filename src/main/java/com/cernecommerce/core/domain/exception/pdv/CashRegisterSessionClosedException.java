package com.cernecommerce.core.domain.exception.pdv;

public class CashRegisterSessionClosedException extends RuntimeException {
    public CashRegisterSessionClosedException(Long sessionId) {
        super("Sessão de caixa encerrada, não é possível registrar vendas: " + sessionId);
    }
}
