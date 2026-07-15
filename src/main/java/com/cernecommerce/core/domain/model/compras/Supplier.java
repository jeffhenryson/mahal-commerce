package com.cernecommerce.core.domain.model.compras;

/**
 * Fornecedor de mercadorias. Base para pedidos de compra e entradas de estoque.
 *
 * <p>Stub de domínio — campos representativos.</p>
 */
public record Supplier(
    Long id,
    String legalName,
    String taxId,
    String email,
    boolean active
) {
}
