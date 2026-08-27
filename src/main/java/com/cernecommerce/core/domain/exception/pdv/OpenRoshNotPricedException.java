package com.cernecommerce.core.domain.exception.pdv;

/**
 * Consumo livre pedido para um produto de sessão que não tem {@code openRoshPrice} cadastrado.
 *
 * <p>Não há fallback para o preço da variação: cobrar o valor do sabor em vez do valor do open rosh
 * é exatamente o erro silencioso que esta feature existe para impedir. Sem preço, recusa.</p>
 */
public class OpenRoshNotPricedException extends RuntimeException {
    public OpenRoshNotPricedException(String sku) {
        super("Produto de sessão do SKU " + sku + " não tem preço de open rosh cadastrado");
    }
}
