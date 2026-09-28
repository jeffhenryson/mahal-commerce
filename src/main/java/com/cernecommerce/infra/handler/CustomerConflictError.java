package com.cernecommerce.infra.handler;

import com.cernecommerce.core.domain.model.crm.CustomerMatchField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 409 do cadastro de cliente (CRM-C007): o corpo padrão de {@link ApiError} mais o cliente que já
 * existe e o que bateu, para o front oferecer "usar este cliente".
 */
@Schema(description = "Conflito de cadastro de cliente — já existe um cliente com o identificador informado")
public record CustomerConflictError(
        @Schema(description = "Mensagem de erro legível") String message,
        @Schema(description = "Sempre CUSTOMER_ALREADY_EXISTS") String errorCode,
        @Schema(description = "Momento em que o erro ocorreu") Instant timestamp,
        @Schema(description = "Caminho da requisição que gerou o erro") String path,
        @Schema(description = "ID de rastreabilidade para correlação de logs") String traceId,
        @Schema(description = "Identificadores que bateram: PHONE, EMAIL, CPF") List<CustomerMatchField> matchedBy,
        @Schema(description = "Id do cliente já cadastrado") Long customerId
) {
}
