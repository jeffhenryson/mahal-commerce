package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** "Repor essência" — descarta a lata em uso e abre outra (EST-F027). */
@Data
public class ReplaceOpenPackageRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    @Schema(description = "Depósito da lata", example = "LOJA-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String warehouseCode;
}
