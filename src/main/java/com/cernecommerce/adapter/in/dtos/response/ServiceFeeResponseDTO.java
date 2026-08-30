package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Percentual da taxa de serviço vigente (PDV-F015).
 *
 * <p>Existe porque a taxa é aplicada por padrão no fechamento: sem uma forma de consultá-la antes,
 * a única maneira de o operador saber quanto será cobrado seria fechar a conta.</p>
 */
public record ServiceFeeResponseDTO(
        @Schema(description = "Percentual sobre o líquido do pedido. Zero significa que a casa não "
                + "cobra taxa de serviço.", example = "10")
        BigDecimal percent) {
}
