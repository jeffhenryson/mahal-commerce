package com.cernecommerce.core.domain.exception.pdv;

import java.time.LocalDate;

/**
 * Venda numa sessão de caixa aberta num dia anterior (PDV-F022). O caixa de ontem esquecido aberto
 * misturaria o movimento de dois dias num fechamento só; o operador fecha e abre o de hoje.
 */
public class CashRegisterSessionStaleException extends RuntimeException {

    public CashRegisterSessionStaleException(Long sessionId, LocalDate openedOn, LocalDate today) {
        super("Sessão de caixa " + sessionId + " foi aberta em " + openedOn + " e hoje é " + today
                + ". Feche o caixa e abra um novo para registrar vendas.");
    }
}
