package com.cernecommerce.core.domain.model.estoque;

/**
 * Tipo de um {@link Product} (EST-F015, Fatia 6).
 *
 * <p>{@link #KIT} é virtual, de um nível só (§2.10 do plano): explode em componentes na venda e
 * no estorno, nunca tem saldo próprio em {@code stock_balance}, e seu custo é sempre derivado da
 * soma dos custos dos componentes — nunca digitado. Componente de kit é obrigatoriamente
 * {@link #SIMPLES}; kit dentro de kit é proibido por construção, não detectado por travessia.</p>
 */
public enum ProductType {
    SIMPLES,
    KIT
}
