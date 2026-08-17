package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Linha de receita de kit ({@link KitComponent}) enriquecida com dados de catálogo do componente
 * (Bloco 3.3 do BACKEND_TODO de mahal-admin) — evita que a tela de edição de receita precise
 * cruzar cada {@code componentSku} com o catálogo geral na mão. Sem dado de saldo por depósito —
 * para isso, ver {@code EstoqueUseCase#getKitAvailability}.
 */
public record KitComponentDetail(String componentSku, BigDecimal quantity, String componentName,
        String componentImageUrl, BigDecimal unitPrice, boolean componentActive) {
}
