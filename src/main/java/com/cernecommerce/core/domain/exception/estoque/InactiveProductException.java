package com.cernecommerce.core.domain.exception.estoque;

/**
 * Tentativa de dar <b>entrada</b> de estoque em um SKU desativado (EST-F018).
 *
 * <p>Produto inativo significa "não compro mais este item". Saída e venda continuam permitidas
 * de propósito, para escoar o saldo remanescente — bloquear as duas deixaria estoque preso.</p>
 */
public class InactiveProductException extends RuntimeException {
    public InactiveProductException(String sku) {
        super("SKU está desativado e não aceita entrada de estoque: " + sku);
    }
}
