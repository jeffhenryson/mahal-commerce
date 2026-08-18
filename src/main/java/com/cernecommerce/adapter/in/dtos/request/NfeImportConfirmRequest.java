package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class NfeImportConfirmRequest {

    @NotNull
    @Schema(description = "Id do import devolvido pelo preview.", example = "1")
    private Long nfeImportId;

    @NotBlank
    @Schema(description = "Depósito de destino — a NF-e não diz para qual depósito a mercadoria vai.",
            example = "LOJA-01")
    private String warehouseCode;

    @Valid
    @Schema(description = "SKU manual para cada linha que o preview marcou UNMATCHED. Obrigatório "
            + "cobrir todas — linha sem override e sem casamento automático bloqueia a confirmação.")
    private List<NfeLineOverrideRequest> overrides = List.of();
}
