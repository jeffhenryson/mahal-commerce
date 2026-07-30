package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderRefundRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Motivo do reembolso. Obrigatório: o reembolso estorna pagamento e cashback "
            + "além de devolver mercadoria ao estoque, e um estorno sem justificativa registrada é "
            + "indistinguível de erro.",
            example = "devolução no prazo")
    private String reason;
}
