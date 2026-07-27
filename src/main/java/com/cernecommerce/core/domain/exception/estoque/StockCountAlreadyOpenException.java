package com.cernecommerce.core.domain.exception.estoque;

/**
 * Já existe um balanço aberto para o depósito (EST-F006).
 *
 * <p>Dois balanços simultâneos sobre o mesmo depósito contariam o mesmo saldo e se
 * sobrescreveriam no fechamento — o segundo a fechar ajustaria contra um saldo que o primeiro
 * acabou de mexer.</p>
 */
public class StockCountAlreadyOpenException extends RuntimeException {
    public StockCountAlreadyOpenException(String warehouseCode, Long openCountId) {
        super("Depósito " + warehouseCode + " já tem o balanço #" + openCountId + " aberto");
    }
}
