package com.cernecommerce.core.domain.exception.pedido;

import java.math.BigDecimal;

/**
 * Desconto de uma linha maior que o bruto dela (PDV-C016).
 *
 * <p>A regra é antiga — o compact constructor de {@code OrderItem} sempre a impôs, com a
 * justificativa de que "desconto que zera o item é devolução, não venda". O que faltava era o
 * <b>código de erro</b>: ela subia como {@code IllegalArgumentException}, que o handler global
 * mapeia para um <b>400 genérico</b> (`BAD_REQUEST`, mensagem "Requisição inválida") e que
 * <b>descarta a mensagem do domínio</b>. O operador recebia a mesma resposta de qualquer corpo
 * malformado, num módulo cujo vocabulário de erro é específico em todo o resto
 * ({@code PRODUCT_NOT_PRICED}, {@code DISCOUNT_LIMIT_EXCEEDED}, {@code SURCHARGE_ON_COURTESY}…).</p>
 *
 * <p>A invariante do record continua onde estava: ela é a rede contra erro de programação e contra
 * reconstituição de dado corrompido. Esta exceção é a recusa <b>do caminho de entrada</b>, onde o
 * valor veio do cliente HTTP e merece um código que a tela saiba tratar.</p>
 */
public class ItemDiscountExceedsGrossException extends RuntimeException {

    public ItemDiscountExceedsGrossException(String sku, BigDecimal discountAmount, BigDecimal grossAmount) {
        super("Desconto de " + discountAmount + " no item " + sku + " passa do valor da linha ("
                + grossAmount + "): desconto que zera o item é devolução, não venda");
    }
}
