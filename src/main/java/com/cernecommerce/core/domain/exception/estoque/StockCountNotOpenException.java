package com.cernecommerce.core.domain.exception.estoque;

import com.cernecommerce.core.domain.model.estoque.StockCountStatus;

/**
 * Tentativa de contar, fechar ou cancelar um balanço que já não está aberto (EST-F006).
 *
 * <p>Fechar é a operação que aplica os ajustes de saldo; deixá-la repetir sobre um balanço já
 * fechado aplicaria o mesmo ajuste duas vezes.</p>
 */
public class StockCountNotOpenException extends RuntimeException {
    public StockCountNotOpenException(Long id, StockCountStatus status) {
        super("Balanço de inventário " + id + " não está aberto (status atual: " + status + ")");
    }
}
