package com.cernecommerce.core.domain.exception.pdv;

/**
 * Modo de sessão (PDV-F010) pedido para um SKU que não é produto de sessão.
 *
 * <p>400, não 409: o que está errado é o corpo da requisição — pedir {@code OPEN_ROSH} de uma lata
 * de carvão é combinação inválida, não um estado do cadastro que mudou no meio do caminho.</p>
 */
public class NotASessionProductException extends RuntimeException {
    public NotASessionProductException(String sku, String mode) {
        super("SKU " + sku + " não é um produto de sessão de mesa — modo " + mode + " não se aplica");
    }
}
