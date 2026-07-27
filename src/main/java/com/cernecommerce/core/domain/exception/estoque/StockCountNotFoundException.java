package com.cernecommerce.core.domain.exception.estoque;

/** Balanço de inventário inexistente (EST-F006). */
public class StockCountNotFoundException extends RuntimeException {
    public StockCountNotFoundException(Long id) {
        super("Balanço de inventário não encontrado: " + id);
    }
}
