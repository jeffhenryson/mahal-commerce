package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** SKU informado manualmente para uma linha que o casamento automático por EAN não resolveu. */
@Data
public class NfeLineOverrideRequest {

    @Min(1)
    @Schema(description = "nItem da linha na NF-e (o mesmo devolvido no preview).", example = "3")
    private int itemNumber;

    @NotBlank
    @Schema(description = "SKU do catálogo a associar a esta linha.", example = "ESS-MENTA-50")
    private String sku;
}
