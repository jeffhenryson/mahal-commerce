package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Disponibilidade de um componente dentro da receita de um kit (Bloco 1.1 do BACKEND_TODO de
 * mahal-admin): quanto está disponível no depósito e quantos kits esse componente sozinho
 * permitiria montar — o mínimo entre os componentes de um kit é o {@code buildableQuantity}
 * de {@link KitAvailability}.
 */
public record KitComponentAvailability(String componentSku, BigDecimal recipeQuantity,
        BigDecimal availableQuantity, BigDecimal buildableQuantity) {
}
