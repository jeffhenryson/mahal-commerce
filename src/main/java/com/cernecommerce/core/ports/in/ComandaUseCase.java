package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
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
    default Comanda addItem(Long comandaId, String sku, BigDecimal quantity, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId, String username) {
        return addItem(comandaId, sku, quantity, mode, courtesy, linkedItemId, null, null, username);
    }

    /**
     * Lança a linha carregando também o <b>setup da mesa</b> e o <b>acréscimo do open rosh</b>
     * (PDV-F011).
     *
     * <p>{@code notes} é texto opaco: o servidor grava e devolve, nunca interpreta. Existe porque
     * nem tudo que sai para a mesa é venda — a pinça é equipamento do salão, não é consumida, e
     * lançá-la como cortesia baixaria estoque e apareceria no cupom do cliente como um item de
     * R$ 0 que ele não pediu. Registro não é venda a zero.</p>
     *
     * <p>{@code surchargeAmount} é somado ao preço que o servidor resolve, e por isso só faz
     * sentido em {@code OPEN_ROSH}: nos demais modos a diferença do sabor caro já mora no
     * {@code pricing} da variante. <b>Não é um {@code discountAmount} negativo</b> — acréscimo e
     * desconto são operações opostas com o mesmo peso contábil, e o relatório precisa distinguir
     * "cobramos a mais" de "cobramos a menos". O {@code costPrice} segue congelado: acréscimo é
     * margem, não custo.</p>
     *
     * @param notes registro livre do setup, no máximo {@code ComandaItem.NOTES_MAX_LENGTH}.
     * @param surchargeAmount acréscimo sobre o preço resolvido. Quem chama é responsável por
     *        checar {@code PDV_COMANDA_SURCHARGE} — ver {@code PdvComandaController}, mesmo padrão
     *        de {@code courtesy}.
     * @throws com.cernecommerce.core.domain.exception.pdv.NotesTooLongException se {@code notes}
     *         passar do limite — recusa em vez de truncar, para o operador saber que perdeu o
     *         registro
     * @throws com.cernecommerce.core.domain.exception.pdv.SurchargeInvalidException se o acréscimo
     *         for negativo
     * @throws com.cernecommerce.core.domain.exception.pdv.SurchargeOnCourtesyException se houver
     *         acréscimo numa linha de cortesia
     * @throws com.cernecommerce.core.domain.exception.pdv.SurchargeNotApplicableException se
     *         houver acréscimo fora de {@code OPEN_ROSH}
     */
    Comanda addItem(Long comandaId, String sku, BigDecimal quantity, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, String username);

    /**
     * Remove uma linha da comanda aberta, devolvendo ao estoque o que ela havia debitado
     * (PDV-F012).
     *
     * <p>Existe porque, até aqui, lançamento errado numa mesa só saía cancelando a comanda
     * <b>inteira</b> — o que devolve tudo ao estoque, encerra a mesa e obriga a relançar item a
     * item um consumo que continua acontecendo.</p>
     *
     * <p><b>As {@code TROCA} penduradas na linha saem junto.</b> Elas são cortesia e não existem
     * sem o consumo livre que as originou, e {@code linked_item_id} é FK auto-referente: deixá-las
     * para trás produziria linha apontando para id inexistente. O {@code SABOR_EXTRA}, ao
     * contrário, é linha própria e pode estar sendo cobrada — a presença dele <b>barra</b> a
     * remoção em vez de ser arrastado, para não tirar valor da conta sem o operador pedir.</p>
     *
     * <p>A devolução é uma {@code ENTRADA} por linha removida, o mesmo padrão de
     * {@link #cancelComanda}. Não exige posse do caixa, como o resto da operação de mesa.</p>
     *
     * @return a comanda sem a linha e sem as trocas dela
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada — linha de mesa fechada é histórico, e o pedido gerado
     *         já foi pago
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaItemNotFoundException se a linha
     *         não estiver nesta comanda
     * @throws com.cernecommerce.core.domain.exception.pdv.LinkedItemIsChargedException se houver
     *         {@code SABOR_EXTRA} pendurado na linha
     */
    Comanda removeItem(Long comandaId, Long itemId, String username);

    /**
     * Busca uma comanda pelo id.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException se não existir
     */
    Comanda getComanda(Long comandaId);

    /**
     * Comandas abertas — a lista de "mesas ocupadas" (PDV-C007).
     *
     * <p>Os dois filtros são <b>opcionais</b>. Sem {@code sessionId} a listagem é da <b>loja</b>, e
     * não de um caixa: a decisão do dono é <i>caixa por atendente, mesas compartilhadas</i>, e quem
     * assume o posto do colega precisa ver o salão inteiro. Era isso que forçava o cliente a
     * buscar as sessões abertas e disparar uma chamada por sessão.</p>
     *
     * <p>Paginada desde PDV-C012: a rota devolvia {@code List} sem teto, o que era contido pelo
     * tamanho do salão enquanto a listagem era de um caixa só — e deixa de ser quando ela passa a
     * ser da loja.</p>
     */
    PageResult<Comanda> listOpenComandas(Long sessionId, String warehouseCode, int page, int size);

    /**
     * Fecha a comanda: converte os itens acumulados num {@code Order} concluído, validando os
     * pagamentos contra o total (mesmo contrato de {@link PdvUseCase#registerSale}). O estoque já
     * foi debitado item a item em {@link #addItem} — o fechamento não toca em saldo de novo.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException se não houver
     *         nenhum item lançado
     * <p><b>Desconto (PDV-F014)</b> é de nível de conta — "tira 20 reais" —, mas é <b>rateado entre
     * os itens</b> antes de virar pedido, proporcionalmente ao valor de cada linha
     * ({@code DiscountProration}). Sem o rateio a casa pagaria cashback sobre dinheiro que não
     * recebeu e a margem por item mostraria a venda cheia. O teto é o mesmo do balcão
     * ({@code pdv.sale.max-discount-percent}); quem chama é responsável por checar
     * {@code PDV_COMANDA_DISCOUNT} — ver {@code PdvComandaController}, mesmo padrão de
     * {@code courtesy}.</p>
     *
     * <p><b>Taxa de serviço (PDV-F015)</b> é o oposto: um acréscimo, calculado pelo servidor como
     * percentual sobre o líquido (portanto <b>depois</b> do desconto) e gravado em campo próprio,
     * fora do {@code netAmount} — o líquido é receita da casa, a taxa é repasse ao garçom. Vem
     * aplicada por padrão porque é o padrão do salão; {@code applyServiceFee = false} é o cliente
     * recusando.</p>
     *
     * @param discountAmount abatimento sobre a conta inteira. Nulo ou zero é o caso comum.
     * @param applyServiceFee {@code false} remove a taxa que seria cobrada.
     * @throws com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException se o
     *         desconto passar do teto configurado
     * @throws com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException se a
     *         soma dos pagamentos não cobrir o total, <b>já com a taxa somada</b>
     */
    Order closeComanda(Long comandaId, List<PaymentCommand> payments, BigDecimal discountAmount,
            boolean applyServiceFee, String username);

    /**
     * Percentual da taxa de serviço vigente (PDV-F015), para a tela mostrar ao operador quanto será
     * cobrado <b>antes</b> de ele fechar — e para o cliente poder recusar com o número na mão.
     *
     * <p>Existe porque a taxa é aplicada por padrão: sem uma forma de consultá-la, a única maneira
     * de descobrir o valor seria fechar a conta, que é tarde demais.</p>
     */
    BigDecimal getServiceFeePercent();

    /**
     * Abandona a comanda sem cobrança, devolvendo ao estoque cada item já debitado
     * ({@code ENTRADA}, mesmo padrão de {@code OrderService.refundOrder}).
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     */
    Comanda cancelComanda(Long comandaId, String username);
}
