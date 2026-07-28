package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpenCashRegisterSessionRequest {

    @NotNull
    @DecimalMin(value = "0.0")
    @Schema(description = "Fundo de troco com que o caixa abre. Zero é aceito.", example = "200.00")
    private BigDecimal openingAmount;

    @NotBlank
    @Schema(description = "Depósito de onde sairá a mercadoria vendida neste caixa. Fica na sessão, "
            + "e não no request de cada venda.", example = "LOJA-01")
    private String warehouseCode;
}
