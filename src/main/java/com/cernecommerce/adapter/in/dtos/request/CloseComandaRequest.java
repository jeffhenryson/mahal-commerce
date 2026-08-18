package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Fechamento de comanda. Mesmo shape de pagamento de {@code SaleRequest} (PDV-F006): pelo menos
 * uma linha, várias linhas = pagamento dividido.
 */
@Data
public class CloseComandaRequest {

    @NotEmpty
    @Valid
    @Schema(description = "Pelo menos uma linha. Várias linhas = pagamento dividido.")
    private List<SalePaymentRequest> payments;
}
