package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderCancelRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Motivo do cancelamento. Obrigatório: o cancelamento devolve mercadoria ao "
            + "estoque, e um estorno sem justificativa registrada é indistinguível de erro.",
            example = "cliente desistiu na entrega")
    private String reason;
}
