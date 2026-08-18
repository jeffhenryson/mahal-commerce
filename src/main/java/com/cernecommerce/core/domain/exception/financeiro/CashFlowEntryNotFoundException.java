package com.cernecommerce.core.domain.exception.financeiro;

public class CashFlowEntryNotFoundException extends RuntimeException {
    public CashFlowEntryNotFoundException(Long id) {
        super("Lançamento de fluxo de caixa não encontrado: " + id);
    }
}
