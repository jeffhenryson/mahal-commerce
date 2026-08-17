package com.cernecommerce.core.domain.model.estoque;

/**
 * Marca agregada a partir do campo texto livre {@code Product.brand} (Bloco 2.2 do BACKEND_TODO
 * de mahal-admin) — não existe entidade {@code Brand} própria hoje; ver decisão registrada no
 * plano de implementação (promover para entidade fica para quando destaque/ordem de marca virar
 * necessidade real).
 */
public record BrandSummary(String name, long productCount) {
}
