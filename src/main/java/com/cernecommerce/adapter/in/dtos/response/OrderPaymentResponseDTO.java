package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class OrderPaymentResponseDTO {
    private Long id;

    @Schema(description = "DINHEIRO, DEBITO, CREDITO ou PIX.")
    private String method;

    private BigDecimal amount;

    @Schema(description = "No balcão, sempre CAPTURED — o dinheiro já está na gaveta.")
    private String status;

    @Schema(description = "Só em CREDITO.")
    private Integer installments;

    private Instant capturedAt;
    private Instant createdAt;
}
