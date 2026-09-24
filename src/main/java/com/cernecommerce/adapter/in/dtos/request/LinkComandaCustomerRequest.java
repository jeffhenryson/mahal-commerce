package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * Vínculo do cliente numa mesa já aberta (PDV-F020). {@code customerId} para um cliente existente,
 * ou {@code lead} para cadastrar/reaproveitar na hora; os dois nulos desvinculam.
 */
@Data
public class LinkComandaCustomerRequest {

    @Schema(description = "Cliente do CRM já existente. Tem precedência sobre lead.", example = "42")
    private Long customerId;

    @Valid
    @Schema(description = "Cadastro rápido (find-or-create por CPF/telefone). Exige CRM_LEAD_CREATE.")
    private CustomerRequest lead;
}
