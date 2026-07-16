package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;

/**
 * Port de entrada do domínio <b>financeiro</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code buildDre} (DRE simplificado por período), {@code reconcileGatewayFees}
 * (conciliação de taxas), consulta de fluxo de caixa por período.</p>
 */
public interface FinanceiroUseCase {

    /** Lista os lançamentos de fluxo de caixa paginados. Stub: retorna página vazia. */
    PageResult<CashFlowEntry> listCashFlow(int page, int size);
}
