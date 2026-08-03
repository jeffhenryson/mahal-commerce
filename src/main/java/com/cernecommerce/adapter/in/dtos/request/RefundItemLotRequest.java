package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * Lote em que a mercadoria devolvida de um SKU volta a ficar (EST-F008). Só é necessário para SKU
 * lote-rastreado — o sistema não tem como adivinhar em qual lote físico o item retorna.
 */
@Data
public class RefundItemLotRequest {

    @NotBlank
    @Schema(description = "SKU do item devolvido. Casamento por SKU: pedido com duas linhas do "
            + "mesmo SKU recebem a mesma info de lote.", example = "ESSE-001")
    private String sku;

    @Schema(description = "Lote de retorno. Obrigatório junto com expiryDate quando o SKU é "
            + "lote-rastreado.", example = "L1")
    private String lotCode;

    private LocalDate expiryDate;
}
