package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenComandaRequest {

    @NotBlank
    @Schema(description = "Identificação livre da mesa ou do cliente — não é um vínculo de cadastro. "
            + "Serve para achar a mesa na tela; o vínculo é o customerId.",
            example = "Mesa 4")
    private String tableOrCustomerLabel;

    @Schema(description = "Cliente do CRM vinculado à mesa (PDV-F010), opcional. É ele que faz o "
            + "pedido gerado no fechamento sair com nome e gerar cashback — sem ele a mesa vira "
            + "venda anônima, como o balcão sem CPF na nota.",
            example = "42")
    private Long customerId;
}
