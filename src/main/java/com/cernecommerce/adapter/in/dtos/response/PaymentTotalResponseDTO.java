package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Total recebido por forma de pagamento numa sessão de caixa. Só {@code DINHEIRO} entra na
 * conferência da gaveta; débito, crédito e PIX se conferem contra a adquirente.
 */
@Data
public class PaymentTotalResponseDTO {

    @Schema(description = "DINHEIRO, DEBITO, CREDITO ou PIX.")
    private String method;

    @Schema(description = "Soma dos pagamentos CAPTURED deste método na sessão. Zero se o método "
            + "não foi usado.")
    private BigDecimal amount;
}
