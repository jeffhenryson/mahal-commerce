package com.cernecommerce.core.domain.model.financeiro;

import java.math.BigDecimal;

/**
 * Saldo agregado de fluxo de caixa num período (FIN-F004, {@code GET /financeiro/cash-flow/summary}).
 * Agregado no backend por consulta dedicada — não pela soma dos itens de uma página, que fica
 * incorreta assim que houver mais de uma página (mesmo cuidado de {@code OrderSummary}/{@code EstoqueSummary}).
 */
public record CashFlowSummary(BigDecimal totalInflow, BigDecimal totalOutflow, BigDecimal balance, long entryCount) {}
