package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Sale;
import com.cernecommerce.core.domain.model.pdv.SaleItem;

import java.util.List;

/**
 * Port de entrada do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>Casos de uso previstos (TODO): {@code openSession}, {@code registerWithdrawal}
 * (sangria), {@code closeSession}.</p>
 */
public interface PdvUseCase {

    /** Lista as sessões de caixa paginadas. Stub: retorna página vazia até a implementação. */
    PageResult<CashRegisterSession> listSessions(int page, int size);

    /**
     * Registra uma venda de balcão dentro de uma sessão de caixa e dá baixa automática no
     * estoque do depósito informado (uma {@code StockMovement} de SAIDA por item, via
     * {@code EstoqueUseCase.adjustStock}). Lança
     * {@link com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException}
     * se a sessão não existir,
     * {@link com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException}
     * se a sessão já estiver fechada, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.InsufficientStockException} se o
     * saldo de algum item for insuficiente — nesse caso a transação inteira é revertida.
     */
    Sale registerSale(Long sessionId, String warehouseCode, List<SaleItem> items, String username);
}
