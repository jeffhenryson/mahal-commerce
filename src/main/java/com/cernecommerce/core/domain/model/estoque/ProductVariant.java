package com.cernecommerce.core.domain.model.estoque;

import java.util.List;

/**
 * Variação (SKU filho) de um {@link Product}, distinguida por seus {@link ProductAttribute}
 * (ex: sabor, tamanho, cor).
 */
public record ProductVariant(Long id, String sku, List<ProductAttribute> attributes, boolean active) {

    public ProductVariant {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku da variação é obrigatório");
        }
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /** Cria uma nova variação (sem id, ativa por padrão). */
    public static ProductVariant create(String sku, List<ProductAttribute> attributes) {
        return new ProductVariant(null, sku, attributes, true);
    }

    /** Reconstitui uma variação a partir de persistência. */
    public static ProductVariant of(Long id, String sku, List<ProductAttribute> attributes, boolean active) {
        return new ProductVariant(id, sku, attributes, active);
    }
}
