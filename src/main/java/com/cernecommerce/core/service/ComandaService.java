package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
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
    public Comanda openComanda(Long sessionId, String tableOrCustomerLabel, String username) {
        CashRegisterSession session = pdvService.requireOwnOpenSession(sessionId, username);
        return comandaRepository.save(
                Comanda.open(sessionId, session.warehouseCode(), tableOrCustomerLabel, username));
    }

    @Override
    @Transactional
    public Comanda addItem(Long comandaId, String sku, BigDecimal quantity, String username) {
        Comanda comanda = getComanda(comandaId);
        pdvService.requireOwnOpenSession(comanda.sessionId(), username);
        requireOpen(comanda);

        EstoqueUseCase.CatalogSaleInfo saleInfo = estoqueUseCase.resolveSaleInfo(sku);
        ComandaItem item = ComandaItem.fromCatalog(sku, quantity, saleInfo.pricing(), saleInfo.productName());

        // Debita agora, não no fechamento — ver a nota de classe sobre não-atomicidade.
        estoqueUseCase.adjustStock(sku, comanda.warehouseCode(), MovementType.SAIDA, quantity,
                "Comanda #" + comandaId, username);

        return comandaRepository.save(comanda.withAddedItem(item));
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
        pdvService.requireOwnOpenSession(comanda.sessionId(), username);
        requireOpen(comanda);
        if (comanda.items().isEmpty()) {
            throw new ComandaEmptyException(comandaId);
        }

        // Cada ComandaItem já tem preço e custo congelados no lançamento — vira OrderItem por
        // reconstituição (of), NUNCA por fromCatalog de novo: reprecificar aqui repreçaria em
        // silêncio itens que o cliente já consumiu, se o catálogo mudou nas horas em que a
        // comanda ficou aberta. Sem desconto por item nesta entrega (fora de escopo do PDV-F009).
        List<OrderItem> orderItems = new ArrayList<>(comanda.items().size());
        for (ComandaItem item : comanda.items()) {
            orderItems.add(OrderItem.of(null, item.sku(), item.quantity(), item.unitPrice(), item.costPrice(),
                    BigDecimal.ZERO, null, item.productName()));
        }

        Order order = Order.openBalcao(comanda.sessionId(), comanda.warehouseCode(), null, orderItems);
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
        pdvService.requireOwnOpenSession(comanda.sessionId(), username);
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
