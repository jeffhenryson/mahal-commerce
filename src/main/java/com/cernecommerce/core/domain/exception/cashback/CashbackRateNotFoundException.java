package com.cernecommerce.core.domain.exception.cashback;

public class CashbackRateNotFoundException extends RuntimeException {
    public CashbackRateNotFoundException(Long id) {
        super("Taxa de cashback " + id + " não encontrada");
    }
}
