package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do domínio <b>comanda de mesa</b> (PDV-F009).
 *
 * <p>Endpoints novos, separados de {@link PdvUseCase#registerSale}: a comanda modela um pedido
 * incremental de horas (lounge de narguilé), enquanto a venda de balcão continua sendo pontual.
 * O estoque é debitado item a item assim que ele é lançado — não no fechamento —, porque é isso
 * que o evento físico (essência preparada, carvão trocado) já significa. Ver
 * {@code ComandaService} para o porquê disso não ser transacionalmente atômico ao longo da vida
 * da comanda.</p>
 */
public interface ComandaUseCase {

    /**
     * Abre uma comanda nova na sessão do operador autenticado.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está abrindo
     */
    Comanda openComanda(Long sessionId, String tableOrCustomerLabel, String username);

    /**
     * Lança um item na comanda aberta, debitando o estoque na hora.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     * @throws com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException se o
     *         produto não tiver preço no catálogo
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se o
     *         saldo for insuficiente
     */
    Comanda addItem(Long comandaId, String sku, BigDecimal quantity, String username);

    /**
     * Busca uma comanda pelo id.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException se não existir
     */
    Comanda getComanda(Long comandaId);

    /** Comandas abertas de uma sessão — a lista de "mesas ocupadas". */
    List<Comanda> listOpenComandas(Long sessionId);

    /**
     * Fecha a comanda: converte os itens acumulados num {@code Order} concluído, validando os
     * pagamentos contra o total (mesmo contrato de {@link PdvUseCase#registerSale}). O estoque já
     * foi debitado item a item em {@link #addItem} — o fechamento não toca em saldo de novo.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException se não houver
     *         nenhum item lançado
     * @throws com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException se a
     *         soma dos pagamentos não cobrir o total
     */
    Order closeComanda(Long comandaId, List<PaymentCommand> payments, String username);

    /**
     * Abandona a comanda sem cobrança, devolvendo ao estoque cada item já debitado
     * ({@code ENTRADA}, mesmo padrão de {@code OrderService.refundOrder}).
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     */
    Comanda cancelComanda(Long comandaId, String username);
}
