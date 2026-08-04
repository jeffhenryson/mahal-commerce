package com.cernecommerce.core.domain.model.ecommerce;

import java.time.Instant;
import java.util.List;

/**
 * Carrinho de compras online (ECM-F003, Fatia 9) — um por cliente.
 *
 * <p>Substitui o stub anterior (ECM-C002), que era um record sem {@link CartItem} nenhum e sem
 * dono real (só um {@code customerRef} solto). Não havia nada a aproveitar.</p>
 *
 * <p><b>Sem preço.</b> Ver {@link CartItem}. <b>Sem reserva de estoque</b> — a reserva só acontece
 * no checkout (plano-pdv-marketplace.md §2.2): carrinho abandonado é a regra, não a exceção, e
 * reservar aqui seguraria estoque por horas à toa.</p>
 */
public record Cart(Long id, Long customerId, List<CartItem> items, Instant updatedAt) {

    public Cart {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Carrinho vazio, ainda sem linha persistida — o estado de quem nunca adicionou nada. */
    public static Cart empty(Long customerId) {
        return new Cart(null, customerId, List.of(), null);
    }

    /** Reconstitui um carrinho a partir de persistência. */
    public static Cart of(Long id, Long customerId, List<CartItem> items, Instant updatedAt) {
        return new Cart(id, customerId, items, updatedAt);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
