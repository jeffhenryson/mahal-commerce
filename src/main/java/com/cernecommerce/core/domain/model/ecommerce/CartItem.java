package com.cernecommerce.core.domain.model.ecommerce;

import java.math.BigDecimal;

/**
 * Linha do {@link Cart} do cliente (ECM-F003). Só SKU e quantidade — o carrinho
 * <b>não guarda preço</b>. Preço é resolvido do catálogo na exibição e congelado
 * só no checkout; guardá-lo aqui criaria a promessa de um preço que o sistema não
 * se comprometeu a honrar.
 */
public record CartItem(String sku, BigDecimal quantity) {

    public CartItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
    }
}
