package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashMovementRequest {

    @NotNull
    @Schema(description = "SANGRIA retira dinheiro do caixa; SUPRIMENTO reforça o troco.",
            example = "SANGRIA")
    private CashMovementType type;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "Sempre positivo — o sentido vem do type.", example = "150.00")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Obrigatório: sangria sem motivo registrado é indistinguível de desvio.",
            example = "depósito no cofre")
    private String reason;
}
