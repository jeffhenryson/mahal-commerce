package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.crm.CustomerMatchField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Item de {@code GET /crm/customers/lookup/contact} (CRM-C007): o cliente, no mesmo formato de
 * {@link CustomerResponseDTO}, mais por onde ele bateu.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerMatchResponseDTO extends CustomerResponseDTO {

    @Schema(description = "Identificadores que bateram: PHONE, EMAIL, CPF")
    private List<CustomerMatchField> matchedBy;
}
