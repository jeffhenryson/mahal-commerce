package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Uma linha de pagamento de uma venda de balcão (PDV-F006). Várias linhas = pagamento dividido
 * (ex.: R$50 em dinheiro + R$30 no débito).
 */
@Data
public class SalePaymentRequest {

    @NotBlank
    @Schema(description = "DINHEIRO, DEBITO, CREDITO ou PIX.", example = "DINHEIRO")
    private String method;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "Em DINHEIRO, é o quanto o cliente entregou — pode passar do total da "
            + "venda e virar troco. Nos demais métodos, é o valor exato cobrado: não pode, sozinho "
            + "ou somado a outras linhas não-DINHEIRO, passar do líquido do pedido.", example = "50.00")
    private BigDecimal amount;

    @Min(1)
    @Max(24)
    @Schema(description = "Só em CREDITO. Ausente nos demais métodos.", example = "3")
    private Integer installments;
}
