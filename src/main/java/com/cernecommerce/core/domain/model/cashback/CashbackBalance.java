package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;

/**
 * Retrato do saldo de cashback de um cliente num instante (CRM-F003).
 *
 * @param available soma das entradas já liberadas ({@code available_at <= now()}) — o que o
 *        cliente pode efetivamente resgatar hoje.
 * @param pending soma dos ganhos {@link CashbackEntryType#EARNED} ainda em carência
 *        ({@code available_at > now()}).
 * @param expiringSoon soma dos ganhos disponíveis que vencem numa janela próxima — ver o
 *        parâmetro de consulta do endpoint que produz este valor.
 */
public record CashbackBalance(BigDecimal available, BigDecimal pending, BigDecimal expiringSoon) {

    public CashbackBalance {
        available = available == null ? BigDecimal.ZERO : available;
        pending = pending == null ? BigDecimal.ZERO : pending;
        expiringSoon = expiringSoon == null ? BigDecimal.ZERO : expiringSoon;
    }
}
