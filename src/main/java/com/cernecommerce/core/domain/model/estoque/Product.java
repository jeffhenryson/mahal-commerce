package com.cernecommerce.core.domain.model.estoque;

/**
 * Produto (SKU pai) da grade de estoque. As variações (SKU filhos) e seus atributos
 * (sabor, tamanho, cor) serão modelados em {@code ProductVariant} /
 * {@code ProductAttribute}.
 *
 * <p>Stub de domínio — campos representativos.</p>
 */
public record Product(
    Long id,
    String sku,
    String name,
    String category,
    boolean active
) {
}
