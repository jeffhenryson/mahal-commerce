package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Novo rótulo da mesa (PDV-F016) — o cliente mudou de lugar no salão. */
@Data
public class RenameComandaRequest {

    /**
     * Mesmo {@code @Size} de {@code OpenComandaRequest} (PDV-C011), casando com
     * {@code comanda.table_or_customer_label VARCHAR(100)}: sem ele o rótulo longo atravessa a
     * validação e estoura no banco como 500 em vez de 400.
     */
    @NotBlank
    @Size(max = 100)
    @Schema(description = "Novo rótulo da mesa ou do cliente.", example = "Mesa 7")
    private String tableOrCustomerLabel;
}
