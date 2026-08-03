package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Corpo de ativação/desativação do rastreamento de lote e validade de um produto (EST-F008).
 *
 * <p>Endpoint próprio, não um campo do PATCH de edição — mesma razão de {@link ActiveRequest}:
 * ligar o rastreio muda o que {@code POST /estoque/movements} exige numa ENTRADA futura daquele
 * SKU, e merece evento de auditoria distinto de uma correção de nome.</p>
 *
 * <p>{@code Boolean} com {@code @NotNull} em vez de {@code boolean}: corpo vazio ou sem o campo é
 * 400, não um "ligar"/"desligar" silencioso vindo do default {@code false}.</p>
 */
@Data
public class LotTrackedRequest {

    @NotNull
    private Boolean lotTracked;
}
