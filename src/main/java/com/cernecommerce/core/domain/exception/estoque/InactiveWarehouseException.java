package com.cernecommerce.core.domain.exception.estoque;

/**
 * Tentativa de dar <b>entrada</b> de estoque em um depósito desativado (EST-F018).
 *
 * <p>Mesma regra do produto inativo: o depósito para de receber mercadoria, mas continua
 * despachando o que já tem.</p>
 */
public class InactiveWarehouseException extends RuntimeException {
    public InactiveWarehouseException(String code) {
        super("Depósito está desativado e não aceita entrada de estoque: " + code);
    }
}
