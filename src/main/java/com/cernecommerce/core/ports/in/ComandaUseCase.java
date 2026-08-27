package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
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
    default Comanda openComanda(Long sessionId, String tableOrCustomerLabel, String username) {
        return openComanda(sessionId, tableOrCustomerLabel, null, username);
    }

    /**
     * Abre uma comanda nova, opcionalmente vinculada a um cliente do CRM (PDV-F010).
     *
     * <p>{@code customerId} é o que faz o pedido da mesa sair com nome e gerar cashback — o rótulo
     * da mesa nunca foi vínculo de cadastro.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está abrindo
     */
    Comanda openComanda(Long sessionId, String tableOrCustomerLabel, Long customerId, String username);

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
    default Comanda addItem(Long comandaId, String sku, BigDecimal quantity, String username) {
        return addItem(comandaId, sku, quantity, ConsumptionMode.NORMAL, false, null, username);
    }

    /**
     * Lança uma linha de <b>sessão de narguilé</b> na comanda aberta (PDV-F010), debitando o
     * estoque na hora como qualquer outra linha.
     *
     * <p>O preço unitário é resolvido <b>aqui</b>, e não pelo chamador — mesma garantia de sempre.
     * A regra por modo: {@code NORMAL} e {@code SABOR_EXTRA} cobram o preço da variação do sabor;
     * {@code OPEN_ROSH} cobra o {@code openRoshPrice} do produto <b>pai</b>; cortesia e
     * {@code TROCA} gravam zero, sempre com o custo congelado normalmente.</p>
     *
     * @param mode por que a linha existe. Nulo resolve para {@code NORMAL}.
     * @param courtesy linha a preço zero que ainda baixa estoque. Quem chama é responsável por
     *        checar a permissão — ver {@code PdvComandaController}, mesmo padrão de
     *        {@code PDV_SALE_DISCOUNT}.
     * @param linkedItemId linha de origem na mesma comanda, obrigatória em {@code SABOR_EXTRA} e
     *        {@code TROCA}.
     * @throws com.cernecommerce.core.domain.exception.pdv.NotAvailableForTableException se o SKU
     *         não estiver disponível para mesa
     * @throws com.cernecommerce.core.domain.exception.pdv.NotASessionProductException se um modo
     *         de sessão for pedido para um SKU que não é produto de sessão
     * @throws com.cernecommerce.core.domain.exception.pdv.OpenRoshNotPricedException se
     *         {@code OPEN_ROSH} for pedido para produto sem preço de consumo livre
     * @throws com.cernecommerce.core.domain.exception.pdv.LinkedItemRequiredException se a linha
     *         de origem faltar ou não pertencer a esta comanda
     * @throws com.cernecommerce.core.domain.exception.pdv.NotAnOpenRoshException se a
     *         {@code TROCA} apontar para uma linha que não é consumo livre
     */
    Comanda addItem(Long comandaId, String sku, BigDecimal quantity, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String username);

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
