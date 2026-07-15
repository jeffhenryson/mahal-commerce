package com.cernecommerce.core.domain.model.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lançamento de fluxo de caixa (entrada ou saída). Base para o DRE simplificado e a
 * conciliação de taxas de gateway.
 *
 * <p>Stub de domínio — campos representativos.</p>
 */
public record CashFlowEntry(
    Long id,
    LocalDate date,
    String description,
    BigDecimal amount,
    Direction direction
) {
    public enum Direction { INFLOW, OUTFLOW }
}
