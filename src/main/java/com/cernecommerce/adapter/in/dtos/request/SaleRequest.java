package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SaleRequest {

    @NotBlank
    @Schema(description = "Depósito de onde sai a mercadoria. Passa a vir da sessão de caixa em "
            + "PDV-C004.", example = "LOJA-01")
    private String warehouseCode;

    @Schema(description = "Cliente identificado, opcional. Sem cliente não há cashback — é esse o "
            + "incentivo que faz o operador perguntar \"CPF na nota?\".", example = "42")
    private Long customerId;

    @NotEmpty
    @Valid
    private List<SaleItemRequest> items;
}
