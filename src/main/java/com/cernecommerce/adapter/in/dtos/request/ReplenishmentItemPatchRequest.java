package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Alteração parcial de um item já anotado — {@code quantity} e {@code note} são os dois únicos
 * campos editáveis depois do POST. Diferente do resto do módulo, {@code note} nulo <b>apaga</b> a
 * nota em vez de mantê-la: o frontend sempre manda os dois juntos neste PATCH específico.
 */
@Data
public class ReplenishmentItemPatchRequest {

    @PositiveOrZero
    private BigDecimal quantity;

    @Size(max = 500)
    private String note;
}
