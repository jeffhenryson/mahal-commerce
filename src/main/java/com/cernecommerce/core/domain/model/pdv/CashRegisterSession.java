package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sessão de caixa do PDV: representa o ciclo de vida de um caixa aberto no balcão
 * (abertura → movimentações/sangrias → fechamento).
 *
 * <p>Stub de domínio — campos representativos. Regras de negócio previstas (TODO):
 * {@code open()}, {@code registerWithdrawal()} (sangria), {@code close()} com
 * conferência de valores esperado vs. contado.</p>
 */
public record CashRegisterSession(
    Long id,
    String operator,
    Instant openedAt,
    BigDecimal openingAmount,
    Instant closedAt,
    Status status
) {
    public enum Status { OPEN, CLOSED }
}
