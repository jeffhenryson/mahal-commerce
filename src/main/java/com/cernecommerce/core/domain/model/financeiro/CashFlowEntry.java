package com.cernecommerce.core.domain.model.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lançamento de fluxo de caixa (entrada ou saída). Base para o DRE simplificado e a
 * conciliação de taxas de gateway.
 *
 * <p>Schema fechado com o front em FIN-F004 (contrato reproduzido em
 * {@code docs/dominios/financeiro/README.md}). {@code date} é a data de lançamento, atribuída
 * pelo servidor na criação — distinta de {@code dueDate} (vencimento) e {@code paymentDate}
 * (pagamento efetivo). {@code status} nasce {@code PREVISTO} e é mutável via
 * {@code PATCH /financeiro/cash-flow/{id}}.</p>
 */
public record CashFlowEntry(
    Long id,
    LocalDate date,
    String description,
    String entityName,
    CashFlowCategory category,
    Direction direction,
    BigDecimal amount,
    CashFlowStatus status,
    LocalDate dueDate,
    LocalDate paymentDate,
    LinkedEntityType linkedEntityType,
    Long linkedEntityId,
    Instant deletedAt
) {
    public enum Direction { INFLOW, OUTFLOW }
}
