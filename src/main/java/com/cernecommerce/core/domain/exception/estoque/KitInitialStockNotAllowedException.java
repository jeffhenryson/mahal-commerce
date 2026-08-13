package com.cernecommerce.core.domain.exception.estoque;

/**
 * Kit não pode receber estoque inicial na criação — kit não tem {@code stock_balance} própria,
 * o saldo é sempre derivado dos componentes (EST-F015).
 */
public class KitInitialStockNotAllowedException extends RuntimeException {
    public KitInitialStockNotAllowedException(String sku) {
        super("Kit não pode receber estoque inicial: " + sku + " não tem saldo próprio, é derivado dos componentes");
    }
}
