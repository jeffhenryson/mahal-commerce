package com.cernecommerce.core.domain.model.financeiro;

/**
 * Status de um lançamento de fluxo de caixa (FIN-F004). Coluna mutável, não ledger append-only:
 * o contrato do front pede {@code PATCH .../{id}} para marcar como pago diretamente, então a
 * imutabilidade no espírito de {@code cashback_entry}/{@code stock_movement} (estorno como novo
 * lançamento) fica para depois — ver "Próximos passos" em `docs/dominios/financeiro/README.md`.
 */
public enum CashFlowStatus {
    PREVISTO,
    PAGO,
    ATRASADO
}
