package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenComandaRequest {

    @NotBlank
    @Schema(description = "Identificação livre da mesa ou do cliente — não é um vínculo de cadastro.",
            example = "Mesa 4")
    private String tableOrCustomerLabel;
}
