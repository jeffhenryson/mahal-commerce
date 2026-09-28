package com.cernecommerce.core.domain.model.pdv;

import java.time.Instant;

/**
 * Status e horários de uma linha de sessão (PDV-F023). Espelha as quatro colunas da V132 em
 * {@code comanda_item}; agrupadas aqui porque só existem juntas, e só em linha de sessão.
 *
 * @param startedAt quando o tempo de mesa começou — nulo enquanto {@link SessionStatus#NA_FILA} ou
 *                  {@link SessionStatus#AGUARDANDO_PAGAMENTO}
 */
public record SessionProgress(SessionStatus status, Instant startedAt, Instant deliveredAt, Instant collectedAt) {

    public SessionProgress {
        if (status == null) {
            throw new IllegalArgumentException("status da sessão é obrigatório");
        }
        if (status != SessionStatus.NA_FILA && status != SessionStatus.AGUARDANDO_PAGAMENTO && startedAt == null) {
            throw new IllegalArgumentException("sessão " + status + " exige startedAt");
        }
    }

    /** Sessão lançada agora e já no preparo — o tempo de mesa começa aqui. */
    public static SessionProgress preparing(Instant at) {
        return new SessionProgress(SessionStatus.PREPARANDO, at, null, null);
    }

    /** PDV-F027 — sessão lançada, utensílio reservado, esperando o pagamento para ir ao preparo. */
    public static SessionProgress awaitingPayment() {
        return new SessionProgress(SessionStatus.AGUARDANDO_PAGAMENTO, null, null, null);
    }

    /**
     * PDV-F027 — o pagamento leva a sessão ao preparo; é aqui que o tempo de mesa começa. Fora do
     * {@link #advanceTo}, que é o caminho do operador, e por isso não aceita esta transição.
     */
    public SessionProgress paid(Instant at) {
        if (status != SessionStatus.AGUARDANDO_PAGAMENTO) {
            throw new IllegalStateException("sessão " + status + " não está aguardando pagamento");
        }
        return new SessionProgress(SessionStatus.PREPARANDO, at, null, null);
    }

    /** 2º rosh do duplo: espera o 1º ser recolhido para começar. */
    public static SessionProgress queued() {
        return new SessionProgress(SessionStatus.NA_FILA, null, null, null);
    }

    /**
     * Avança para {@code next}, carimbando o horário correspondente.
     *
     * @throws IllegalStateException se a transição não é permitida — o service recusa antes com 409
     */
    public SessionProgress advanceTo(SessionStatus next, Instant at) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("transição de sessão inválida: " + status + " → " + next);
        }
        return switch (next) {
            case PREPARANDO -> new SessionProgress(next, at, deliveredAt, collectedAt);
            case ENTREGUE -> new SessionProgress(next, startedAt, at, collectedAt);
            case RECOLHIDO -> new SessionProgress(next, startedAt, deliveredAt, at);
            case NA_FILA, AGUARDANDO_PAGAMENTO -> throw new IllegalStateException("nada volta para " + next);
        };
    }

    public boolean isAwaitingPayment() {
        return status == SessionStatus.AGUARDANDO_PAGAMENTO;
    }

    public boolean isCollected() {
        return status == SessionStatus.RECOLHIDO;
    }
}
