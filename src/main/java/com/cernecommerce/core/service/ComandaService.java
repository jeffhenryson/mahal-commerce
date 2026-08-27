package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException;
import com.cernecommerce.core.domain.exception.pdv.ComandaOnlyCourtesyException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemRequiredException;
import com.cernecommerce.core.domain.exception.pdv.NotASessionProductException;
import com.cernecommerce.core.domain.exception.pdv.NotAnOpenRoshException;
import com.cernecommerce.core.domain.exception.pdv.NotAvailableForTableException;
import com.cernecommerce.core.domain.exception.pdv.OpenRoshNotPricedException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Comanda de mesa (PDV-F009): pedidos incrementais de uma sessão de caixa aberta por horas — o
 * caso do lounge de narguilé. Endpoints novos, sem tocar em {@code PdvService.registerSale}.
 *
 * <h2>Baixa de estoque imediata, não atômica ao longo da vida da comanda</h2>
 * <p>Cada {@link #addItem} debita o estoque na hora, no mesmo instante em que o item é lançado —
 * reflete o evento físico real (a essência foi preparada e servida). Diferente de
 * {@code registerSale}, que debita tudo numa única transação no fechamento, aqui cada chamada é
 * seu próprio commit: não dá para segurar uma transação de banco aberta pelas horas em que uma
 * comanda fica em uso. A contrapartida é que itens já lançados <b>não</b> fazem rollback se um
 * lançamento posterior falhar — {@link #cancelComanda} cobre o abandono explícito, devolvendo cada
 * item ao estoque, mas não há varredura automática para comanda esquecida aberta sem cancelamento.
 * Limitação conhecida, documentada no README do módulo.</p>
 *
 * <h2>Caixa por atendente, mesas compartilhadas (PDV-F010)</h2>
 * <p><b>Abrir</b> uma comanda exige a própria sessão — a mesa nasce na gaveta de quem a abriu, e é
 * esse depósito que vai baixar estoque. <b>Operar</b> uma mesa já aberta (lançar, fechar, cancelar)
 * não exige posse: o atendente que assume o posto do colega precisa enxergar e tocar as mesas do
 * salão. O controle de acesso ali é a permissão {@code PDV_COMANDA_MANAGE}, não a posse da gaveta.</p>
 *
 * <p>Não é regressão do isolamento de PDV-C004: aquele resolveu a <i>venda de balcão</i>, onde
 * vender no caixa alheio criava diferença sem dono, e {@code registerSale} e os movimentos de caixa
 * continuam exigindo posse. O consumo da mesa é do salão, não do operador.</p>
 *
 * <p>A contrapartida é <b>onde o dinheiro entra</b>: o pedido nasce na sessão de <b>quem fecha</b>
 * (via {@code getCurrentSession}), não na que abriu a comanda. É a leitura que bate com a
 * conferência física — a cédula está na gaveta de quem recebeu — e é o que impede o pedido de cair
 * numa sessão que o colega já encerrou.</p>
 *
 * <h2>Reaproveita {@code PdvService}, não duplica</h2>
 * <p>Posse de sessão e validação de pagamento/troco são as mesmas regras da venda de balcão — a de
 * troco em pagamento dividido, em particular, já foi endurecida uma vez (mais estrita que o
 * desenho original do plano). Duplicá-la aqui arriscaria as duas cópias divergirem em silêncio, o
 * tipo de bug que as tabelas de Regras de Negócio deste projeto existem para prevenir. Por isso
 * este service recebe o bean <b>concreto</b> {@code PdvService} (não a interface {@code PdvUseCase},
 * que esconderia os métodos package-private) e chama {@code requireOwnOpenSession}/
 * {@code validatePaymentsAndComputeChange} diretamente — primeira dependência service-para-service
 * do projeto, deliberada.</p>
 */
public class ComandaService implements ComandaUseCase {

    private final ComandaRepository comandaRepository;
    private final EstoqueUseCase estoqueUseCase;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final CashbackUseCase cashbackUseCase;
    private final PdvService pdvService;

    public ComandaService(ComandaRepository comandaRepository, EstoqueUseCase estoqueUseCase,
            OrderRepository orderRepository, OrderPaymentRepository orderPaymentRepository,
            CashbackUseCase cashbackUseCase, PdvService pdvService) {
        this.comandaRepository = comandaRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.orderRepository = orderRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.cashbackUseCase = cashbackUseCase;
        this.pdvService = pdvService;
    }

    @Override
    @Transactional
    public Comanda openComanda(Long sessionId, String tableOrCustomerLabel, Long customerId, String username) {
        // Abrir continua exigindo a PRÓPRIA sessão: a mesa nasce na gaveta de quem a abriu, e é
        // esse depósito que vai baixar estoque. O compartilhamento de PDV-F010 é sobre OPERAR mesa
        // já aberta (lançar/fechar/cancelar), não sobre criar uma no caixa alheio.
        CashRegisterSession session = pdvService.requireOwnOpenSession(sessionId, username);
        return comandaRepository.save(
                Comanda.open(sessionId, session.warehouseCode(), tableOrCustomerLabel, customerId, username));
    }

    @Override
    @Transactional
    public Comanda addItem(Long comandaId, String sku, BigDecimal quantity, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId, String username) {
        Comanda comanda = getComanda(comandaId);
        // PDV-F010: mesa é do salão, não do operador — ver PdvService.requireOpenSession.
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);

        ConsumptionMode resolvedMode = mode == null ? ConsumptionMode.NORMAL : mode;
        // TROCA é cortesia por definição: não depende do cliente HTTP ter marcado o campo.
        boolean resolvedCourtesy = courtesy || resolvedMode.impliesCourtesy();

        EstoqueUseCase.CatalogSaleInfo saleInfo = estoqueUseCase.resolveSaleInfo(sku);
        // Todas as validações ANTES de qualquer escrita de estoque — mesma ordem de
        // PdvService.registerSale, e a razão é a mesma: aqui cada lançamento é seu próprio commit,
        // então um débito seguido de recusa deixaria saldo baixado sem linha na comanda.
        if (!saleInfo.availableForTable()) {
            throw new NotAvailableForTableException(sku);
        }
        if (resolvedMode.isSessionMode() && !saleInfo.sessionProduct()) {
            throw new NotASessionProductException(sku, resolvedMode.name());
        }
        Long resolvedLink = resolveLinkedItem(comanda, resolvedMode, linkedItemId);

        ComandaItem item = resolvedMode == ConsumptionMode.NORMAL && !resolvedCourtesy
                ? ComandaItem.fromCatalog(sku, quantity, saleInfo.pricing(), saleInfo.productName())
                : ComandaItem.forSession(sku, quantity,
                        resolveUnitPrice(sku, resolvedMode, resolvedCourtesy, saleInfo), saleInfo.pricing(),
                        saleInfo.productName(), resolvedMode, resolvedCourtesy, resolvedLink);

        // Debita agora, não no fechamento — ver a nota de classe sobre não-atomicidade. Cortesia
        // baixa estoque igual: o cliente não paga, mas a essência saiu.
        estoqueUseCase.adjustStock(sku, comanda.warehouseCode(), MovementType.SAIDA, quantity,
                "Comanda #" + comandaId, username);

        return comandaRepository.save(comanda.withAddedItem(item));
    }

    /**
     * O preço de cada modo. É a regra que a feature inteira gira em torno.
     *
     * <p><b>A armadilha é o open rosh:</b> a linha chega com o SKU da <i>variação</i> do sabor
     * (ex.: {@code SESS-BLUE}, preço 35), mas o valor cobrado é o {@code openRoshPrice} do produto
     * <b>pai</b> (ex.: 60). Resolver o preço pelo SKU, como em todos os outros modos, cobraria 35.
     * O SKU está ali para saber qual essência sair do estoque, não para precificar.</p>
     */
    private BigDecimal resolveUnitPrice(String sku, ConsumptionMode mode, boolean courtesy,
            EstoqueUseCase.CatalogSaleInfo saleInfo) {
        if (courtesy) {
            return BigDecimal.ZERO;
        }
        if (mode == ConsumptionMode.OPEN_ROSH) {
            if (saleInfo.openRoshPrice() == null || saleInfo.openRoshPrice().signum() <= 0) {
                throw new OpenRoshNotPricedException(sku);
            }
            return saleInfo.openRoshPrice();
        }
        // NORMAL e SABOR_EXTRA cobram o preço da variação do sabor, como qualquer item de catálogo.
        return saleInfo.pricing().effectivePrice();
    }

    /**
     * A linha de origem tem que existir <b>nesta</b> comanda — não basta o id ser válido em algum
     * lugar do banco, ou uma troca poderia se pendurar no open rosh da mesa ao lado.
     */
    private Long resolveLinkedItem(Comanda comanda, ConsumptionMode mode, Long linkedItemId) {
        if (!mode.requiresLinkedItem()) {
            return null;
        }
        if (linkedItemId == null) {
            throw new LinkedItemRequiredException(mode.name(), null, comanda.id());
        }
        ComandaItem linked = comanda.items().stream()
                .filter(i -> linkedItemId.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new LinkedItemRequiredException(mode.name(), linkedItemId, comanda.id()));
        // Trocas ilimitadas são a contrapartida do valor fixo do consumo livre. Permitir troca
        // cortesia sobre uma sessão comum daria narguilé de graça.
        if (mode == ConsumptionMode.TROCA && linked.mode() != ConsumptionMode.OPEN_ROSH) {
            throw new NotAnOpenRoshException(linkedItemId, linked.mode().name());
        }
        return linkedItemId;
    }

    @Override
    @Transactional(readOnly = true)
    public Comanda getComanda(Long comandaId) {
        return comandaRepository.findById(comandaId)
                .orElseThrow(() -> new ComandaNotFoundException(comandaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comanda> listOpenComandas(Long sessionId) {
        return comandaRepository.findOpenBySessionId(sessionId);
    }

    @Override
    @Transactional
    public Order closeComanda(Long comandaId, List<PaymentCommand> payments, String username) {
        Comanda comanda = getComanda(comandaId);
        requireOpen(comanda);
        if (comanda.items().isEmpty()) {
            throw new ComandaEmptyException(comandaId);
        }
        // PDV-F010, decisão do dono: quando B fecha a mesa aberta por A, o pedido entra na gaveta
        // de B — o dinheiro pertence a quem o recebeu, e é a conferência de B que precisa fechar no
        // fim do turno. Também é o que impede o pedido de cair numa sessão que A já encerrou.
        // Sem sessão aberta não há como receber: getCurrentSession recusa com 409.
        CashRegisterSession receivingSession = pdvService.getCurrentSession(username);
        // Comanda só de cortesias não fecha — irmã de COMANDA_EMPTY. Pelo desenho da feature a
        // cortesia é sempre acessória de uma sessão paga, então total zero aqui é erro de
        // lançamento, e fechá-lo geraria um pedido concluído de R$ 0 que ninguém revisaria.
        if (comanda.items().stream().allMatch(ComandaItem::courtesy)) {
            throw new ComandaOnlyCourtesyException(comandaId);
        }

        // Cada ComandaItem já tem preço e custo congelados no lançamento — vira OrderItem por
        // reconstituição (of), NUNCA por fromCatalog de novo: reprecificar aqui repreçaria em
        // silêncio itens que o cliente já consumiu, se o catálogo mudou nas horas em que a
        // comanda ficou aberta. Com o open rosh isso ficou ainda mais crítico: fromCatalog
        // resolveria pelo SKU da variação e cobraria o preço do sabor no lugar do valor fixo.
        // Sem desconto por item nesta entrega (fora de escopo do PDV-F009).
        List<OrderItem> orderItems = new ArrayList<>(comanda.items().size());
        for (ComandaItem item : comanda.items()) {
            orderItems.add(OrderItem.of(null, item.sku(), item.quantity(), item.unitPrice(), item.costPrice(),
                    BigDecimal.ZERO, null, item.productName(), item.mode(), item.courtesy()));
        }

        // O canal é imutável: o pedido da mesa precisa NASCER MESA, não virar depois. O depósito
        // continua sendo o da comanda (é de lá que o estoque saiu, item a item), mesmo quando quem
        // fecha é de outra gaveta.
        Order order = Order.openMesa(receivingSession.id(), comanda.warehouseCode(), comanda.customerId(),
                comandaId, comanda.tableOrCustomerLabel(), orderItems);
        BigDecimal changeAmount = pdvService.validatePaymentsAndComputeChange(payments, order.netAmount());

        // Sem novo adjustStock aqui: o estoque já saiu item a item em addItem.
        Order saved = orderRepository.save(
                order.concluded(orderRepository.nextOrderNumber(), changeAmount, Instant.now()));
        for (PaymentCommand payment : payments) {
            orderPaymentRepository.save(OrderPayment.captured(saved.id(), payment.method(),
                    payment.amount(), payment.installments()));
        }
        cashbackUseCase.recordEarnedForOrder(saved);

        comandaRepository.save(comanda.closed(saved.id(), Instant.now()));
        return saved;
    }

    @Override
    @Transactional
    public Comanda cancelComanda(Long comandaId, String username) {
        Comanda comanda = getComanda(comandaId);
        // PDV-F010: mesa compartilhada — ver PdvService.requireOpenSession.
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);

        // Devolve cada item já debitado — mesmo padrão de OrderService.refundOrder.
        for (ComandaItem item : comanda.items()) {
            estoqueUseCase.adjustStock(item.sku(), comanda.warehouseCode(), MovementType.ENTRADA,
                    item.quantity(), "Cancelamento de comanda #" + comandaId, username);
        }
        return comandaRepository.save(comanda.cancelled(Instant.now()));
    }

    private void requireOpen(Comanda comanda) {
        if (comanda.status() != ComandaStatus.ABERTA) {
            throw new ComandaNotOpenException(comanda.id(), comanda.status());
        }
    }
}
