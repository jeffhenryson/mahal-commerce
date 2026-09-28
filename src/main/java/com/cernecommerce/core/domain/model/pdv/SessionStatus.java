package com.cernecommerce.core.domain.model.pdv;

/**
 * Onde está a sessão de narguilé no salão (PDV-F023) — só para linhas {@code SESSAO}/{@code ROSH_EXTRA}.
 *
 * <p>É o ciclo físico, não o financeiro: a sessão é paga na hora, e é este status — não o pagamento —
 * que diz quando o narguilé voltou para a casa. Por isso é {@link #RECOLHIDO} que libera os
 * utensílios e deixa a mesa aceitar a próxima sessão.</p>
 */
public enum SessionStatus {

    /**
     * PDV-F027 — sessão lançada e com utensílio reservado, esperando o pagamento. Só o pagamento
     * (fechamento parcial com a linha no escopo) a leva a {@link #PREPARANDO}; não há transição manual.
     */
    AGUARDANDO_PAGAMENTO,
    /** 2º rosh lançado junto com a sessão, esperando o 1º acabar. Ainda não começou a contar tempo. */
    NA_FILA,
    PREPARANDO,
    ENTREGUE,
    RECOLHIDO;

    /**
     * {@code NA_FILA → PREPARANDO → ENTREGUE → RECOLHIDO}, mais o atalho {@code PREPARANDO → RECOLHIDO}
     * para a sessão desistida antes de chegar à mesa. Nada volta atrás. {@link #AGUARDANDO_PAGAMENTO}
     * não sai por aqui: quem a promove é o pagamento (PDV-F027).
     */
    public boolean canTransitionTo(SessionStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case AGUARDANDO_PAGAMENTO -> false;
            case NA_FILA -> next == PREPARANDO;
            case PREPARANDO -> next == ENTREGUE || next == RECOLHIDO;
            case ENTREGUE -> next == RECOLHIDO;
            case RECOLHIDO -> false;
        };
    }
}
