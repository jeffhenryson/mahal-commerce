package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Corpo de ativação/desativação de produto ou depósito (EST-F018).
 *
 * <p>É um endpoint próprio, e não um campo do PATCH de edição, porque desativar tem efeito
 * operacional — o recurso para de aceitar entrada de estoque — e merece evento de auditoria
 * distinto de uma correção de nome.</p>
 *
 * <p>{@code Boolean} com {@code @NotNull} em vez de {@code boolean}: assim um corpo vazio ou sem
 * o campo é 400, e não um "desativar" silencioso vindo do default {@code false}.</p>
 */
@Data
public class ActiveRequest {

    @NotNull
    private Boolean active;
}
