package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alteração parcial de marca. Campo ausente ou nulo significa <b>não mexer</b>.
 *
 * <p>{@code active} não está aqui: tem endpoint próprio, mesmo motivo de categoria (EST-F018) —
 * desativar merece evento de auditoria distinto de uma correção de nome.</p>
 */
@Data
public class BrandPatchRequest {

    @Size(min = 1, max = 100)
    private String name;
}
