package com.cernecommerce.core.ports.out.ecommerce;

import com.cernecommerce.core.domain.model.ecommerce.Cart;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Port de saída para persistência do carrinho de compras (ECM-F003, Fatia 9).
 */
public interface CartRepository {

    /** Carrinho do cliente, se já existir alguma linha. Vazio para quem nunca adicionou nada. */
    Optional<Cart> findByCustomerId(Long customerId);

    /**
     * Upsert de quantidade (PUT — idempotente): cria o carrinho se for a primeira vez do cliente,
     * cria a linha se o SKU ainda não estiver nela, ou substitui a quantidade se já estiver.
     */
    Cart upsertItem(Long customerId, String sku, BigDecimal quantity);

    /**
     * Remove uma linha do carrinho.
     *
     * @return {@code true} se havia uma linha para remover, {@code false} se o SKU não estava no
     *         carrinho (o service decide se isso é 404 — a repository só relata o fato)
     */
    boolean removeItem(Long customerId, String sku);

    /** Esvazia o carrinho — o checkout consome as linhas ao virar pedido. Sem efeito se já vazio. */
    void clear(Long customerId);
}
