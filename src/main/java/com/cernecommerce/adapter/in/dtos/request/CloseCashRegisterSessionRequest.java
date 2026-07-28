package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseCashRegisterSessionRequest {

    @NotNull
    @DecimalMin(value = "0.0")
    @Schema(description = "O que foi contado de fato na gaveta. Divergir do esperado NÃO impede o "
            + "fechamento — a diferença é registrada, como no fechamento de um balanço de inventário.",
            example = "495.00")
    private BigDecimal countedAmount;
}
