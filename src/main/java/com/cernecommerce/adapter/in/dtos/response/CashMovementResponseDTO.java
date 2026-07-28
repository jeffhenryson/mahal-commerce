package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CashMovementResponseDTO {

    private Long id;
    private Long sessionId;

    @Schema(description = "SANGRIA (retirada) ou SUPRIMENTO (reforço de troco).")
    private String type;

    @Schema(description = "Sempre positivo. O sentido vem do type.")
    private BigDecimal amount;

    @Schema(description = "Efeito no saldo esperado da gaveta: negativo em sangria, positivo em "
            + "suprimento. É este valor que o fechamento soma.")
    private BigDecimal signedAmount;

    private String reason;
    private String username;
    private Instant createdAt;
}
