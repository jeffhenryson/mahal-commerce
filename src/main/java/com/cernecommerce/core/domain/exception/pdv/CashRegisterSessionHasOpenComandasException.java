package com.cernecommerce.core.domain.exception.pdv;

import java.util.List;

/**
 * Fechamento de caixa com mesa ainda aberta na sessão (PDV-C005).
 *
 * <p>Sem esta barreira o fechamento é um <b>beco sem saída</b>: {@code ComandaService.addItem} e
 * {@code cancelComanda} exigem a sessão de origem aberta, então uma comanda que sobreviva ao
 * fechamento do turno passa a responder {@code 409 CASH_REGISTER_SESSION_CLOSED} para sempre. A
 * mesa congela, e o estoque já debitado item a item fica sem nenhum caminho de devolução — a única
 * saída restante seria cobrar um cliente que talvez já tenha ido embora.</p>
 *
 * <p>409 e não 400: o corpo do request está correto; o que impede é o estado do salão. A saída é
 * fechar ou cancelar cada mesa e tentar de novo, e é por isso que a mensagem carrega os ids.</p>
 */
public class CashRegisterSessionHasOpenComandasException extends RuntimeException {
    public CashRegisterSessionHasOpenComandasException(Long sessionId, List<Long> comandaIds) {
        super("Sessão de caixa " + sessionId + " ainda tem " + comandaIds.size()
                + " comanda(s) aberta(s): " + comandaIds
                + ". Feche ou cancele cada mesa antes — depois do fechamento elas não podem mais ser tocadas");
    }
}
