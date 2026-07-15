package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;

import java.util.List;

/**
 * Port de entrada do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code openSession}, {@code registerWithdrawal} (sangria), {@code closeSession},
 * {@code addCounterSaleItem}.</p>
 */
public interface PdvUseCase {

    /** Lista as sessões de caixa. Stub: retorna lista vazia até a implementação. */
    List<CashRegisterSession> listSessions();
}
