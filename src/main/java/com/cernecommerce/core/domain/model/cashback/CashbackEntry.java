package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lançamento no ledger de cashback de um cliente (CRM-F003, §2.4 do plano de arquitetura).
 *
 * <h2>Ledger, nunca saldo mutável</h2>
 * <p>Nenhuma linha é atualizada depois de escrita; nenhuma é deletada. Saldo é {@code SUM(amount)}
 * sobre as entradas já liberadas — ver a nota sobre {@link #availableAt} abaixo. Um saldo mutável
 * é impossível de auditar quando o cliente reclama, e reconstruir o extrato depois do fato não é
 * feito — é adivinhado.</p>
 *
 * <h2>Por que não existe uma coluna "status"</h2>
 * <p>Uma entrada {@link CashbackEntryType#EARNED} está "pendente" enquanto {@link #availableAt}
 * não chegou e "expirada" quando uma entrada {@link CashbackEntryType#EXPIRED} referenciando-a (via
 * {@link #reversesEntryId}) existir — nenhuma das duas transições precisa mutar a linha original.
 * Por isso o saldo disponível é sempre {@code SUM(amount) WHERE available_at <= now()}: cobre
 * {@link CashbackEntryType#EARNED} (positivo) e os futuros {@link CashbackEntryType#REDEEMED}/
 * {@link CashbackEntryType#REVERSED}/{@link CashbackEntryType#EXPIRED} (negativos, com
 * {@code availableAt == createdAt} porque um débito tem que valer imediatamente).</p>
 *
 * @param orderItemId item de origem do ganho; {@code null} para lançamentos que não nascem de um
 *        item específico (ex.: ajuste manual, quando essa fatia existir).
 * @param availableAt instante a partir do qual o valor entra no saldo disponível. Para
 *        {@link CashbackEntryType#EARNED}, {@code paidAt + carência}. Para os demais tipos,
 *        igual a {@link #createdAt} — o débito é imediato.
 * @param expiresAt só relevante para {@link CashbackEntryType#EARNED}: instante em que o valor
 *        vence se não for resgatado. {@code null} para os demais tipos.
 * @param reversesEntryId entrada original que este lançamento reverte/expira. Obrigatório para
 *        todo tipo que não seja {@link CashbackEntryType#EARNED}.
 */
public record CashbackEntry(Long id, Long customerId, Long orderId, Long orderItemId,
        CashbackEntryType type, BigDecimal amount, Instant availableAt, Instant expiresAt,
        Long reversesEntryId, Instant createdAt) {

    public CashbackEntry {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("amount não pode ser nulo ou zero");
        }
        boolean shouldBePositive = type == CashbackEntryType.EARNED;
        if (shouldBePositive != (amount.signum() > 0)) {
            throw new IllegalArgumentException(
                    shouldBePositive ? "amount de EARNED deve ser positivo" : "amount de " + type + " deve ser negativo");
        }
        if (availableAt == null) {
            throw new IllegalArgumentException("availableAt é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
        if (type != CashbackEntryType.EARNED && reversesEntryId == null) {
            throw new IllegalArgumentException("reversesEntryId é obrigatório para " + type);
        }
        if (type == CashbackEntryType.EARNED && expiresAt != null && !expiresAt.isAfter(availableAt)) {
            throw new IllegalArgumentException("expiresAt deve ser posterior a availableAt");
        }
    }

    /** Ganho de cashback sobre um item de pedido, ainda em carência até {@code availableAt}. */
    public static CashbackEntry earned(Long customerId, Long orderId, Long orderItemId,
            BigDecimal amount, Instant createdAt, Instant availableAt, Instant expiresAt) {
        return new CashbackEntry(null, customerId, orderId, orderItemId, CashbackEntryType.EARNED,
                amount, availableAt, expiresAt, null, createdAt);
    }

    /**
     * Expira uma entrada {@link CashbackEntryType#EARNED} não resgatada: lançamento negativo,
     * disponível imediatamente, referenciando a entrada original.
     */
    public static CashbackEntry expired(CashbackEntry original, Instant expiredAt) {
        if (original.type() != CashbackEntryType.EARNED) {
            throw new IllegalArgumentException("só se expira uma entrada EARNED");
        }
        return new CashbackEntry(null, original.customerId(), original.orderId(),
                original.orderItemId(), CashbackEntryType.EXPIRED, original.amount().negate(),
                expiredAt, null, original.id(), expiredAt);
    }

    /**
     * Reverte uma entrada {@link CashbackEntryType#EARNED} por cancelamento/reembolso do pedido
     * (PDV-F007): lançamento negativo, disponível imediatamente, referenciando a entrada
     * original. Mesmo molde de {@link #expired} — tipo diferente, mesma forma.
     */
    public static CashbackEntry reversed(CashbackEntry original, Instant reversedAt) {
        if (original.type() != CashbackEntryType.EARNED) {
            throw new IllegalArgumentException("só se reverte uma entrada EARNED");
        }
        return new CashbackEntry(null, original.customerId(), original.orderId(),
                original.orderItemId(), CashbackEntryType.REVERSED, original.amount().negate(),
                reversedAt, null, original.id(), reversedAt);
    }

    /** Reconstitui um lançamento a partir de persistência. */
    public static CashbackEntry of(Long id, Long customerId, Long orderId, Long orderItemId,
            CashbackEntryType type, BigDecimal amount, Instant availableAt, Instant expiresAt,
            Long reversesEntryId, Instant createdAt) {
        return new CashbackEntry(id, customerId, orderId, orderItemId, type, amount, availableAt,
                expiresAt, reversesEntryId, createdAt);
    }

    /** Indica se este lançamento já conta para o saldo disponível no instante informado. */
    public boolean isAvailableAt(Instant moment) {
        return !moment.isBefore(availableAt);
    }
}
