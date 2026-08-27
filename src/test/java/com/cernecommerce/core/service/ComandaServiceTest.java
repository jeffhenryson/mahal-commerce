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
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComandaServiceTest {

    private static final Pricing ESSENCIA = Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00"));

    @Mock ComandaRepository comandaRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock PdvService pdvService;

    ComandaService comandaService;

    @BeforeEach
    void setUp() {
        comandaService = new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService);
    }

    private CashRegisterSession openSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
    }

    private Comanda abertaComanda(ComandaItem... items) {
        Comanda comanda = Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1");
        for (ComandaItem item : items) {
            comanda = comanda.withAddedItem(item);
        }
        return Comanda.of(10L, comanda.sessionId(), comanda.warehouseCode(), comanda.tableOrCustomerLabel(),
                comanda.customerId(), comanda.status(), comanda.items(), comanda.orderId(), comanda.openedBy(),
                comanda.openedAt(), comanda.closedAt());
    }

    private static ComandaItem essenciaItem() {
        return ComandaItem.fromCatalog("ESS-MENTA", BigDecimal.ONE, ESSENCIA, "Essência Menta");
    }

    private void givenOrderPersistenceAssignsId() {
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order arg = inv.getArgument(0);
            // Sobrecarga COMPLETA de propósito: a curta perderia comandaId/tableLabel, e pedido
            // de MESA sem eles é recusado pelo compact constructor de Order.
            return Order.of(500L, arg.orderNumber(), arg.channel(), arg.status(), arg.customerId(),
                    arg.sessionId(), arg.warehouseCode(), arg.items(), arg.grossAmount(), arg.discountAmount(),
                    arg.cashbackRedeemed(), arg.netAmount(), arg.changeAmount(), arg.cancelReason(),
                    arg.createdAt(), arg.paidAt(), arg.concludedAt(), arg.cancelledAt(), arg.refundedAt(),
                    arg.reservedAt(), arg.separatedAt(), arg.shippedAt(), arg.deliveredAt(), arg.version(),
                    arg.comandaId(), arg.tableLabel());
        });
    }

    // ── Abertura ─────────────────────────────────────────────────────────────────────────────

    @Test
    void openComanda_opensAtTheSessionWarehouse() {
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda comanda = comandaService.openComanda(1L, "Mesa 4", "caixa1");

        assertThat(comanda.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(comanda.status()).isEqualTo(ComandaStatus.ABERTA);
        verify(pdvService).requireOwnOpenSession(1L, "caixa1");
    }

    @Test
    void openComanda_propagatesOwnershipFailureAndDoesNotSave() {
        when(pdvService.requireOwnOpenSession(1L, "outro-operador"))
                .thenThrow(new com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException(1L, "outro-operador"));

        assertThatThrownBy(() -> comandaService.openComanda(1L, "Mesa 4", "outro-operador"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Lançamento de item ───────────────────────────────────────────────────────────────────

    @Test
    void addItem_resolvesPriceFromCatalogAndDebitsStockImmediately() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1");

        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        verify(estoqueUseCase).adjustStock(eq("ESS-MENTA"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(BigDecimal.ONE), eq("Comanda #10"), eq("caixa1"));
    }

    @Test
    void addItem_refusesOnNonAbertaComandaAndDoesNotTouchStock() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(essenciaItem()), 500L, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(fechada));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addItem_throwsWhenComandaNotFound() {
        when(comandaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comandaService.addItem(999L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ComandaNotFoundException.class);
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void addItem_propagatesUnknownSkuAndDoesNotSave() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("DESCONHECIDO")).thenThrow(new ProductNotFoundException("DESCONHECIDO"));

        assertThatThrownBy(() -> comandaService.addItem(10L, "DESCONHECIDO", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void addItem_propagatesInsufficientStockAndDoesNotSave() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        doThrow(new InsufficientStockException("ESS-MENTA", 1L, BigDecimal.ZERO, BigDecimal.ONE))
                .when(estoqueUseCase).adjustStock(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(InsufficientStockException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Fechamento ───────────────────────────────────────────────────────────────────────────

    @Test
    void closeComanda_convertsAccumulatedItemsWithoutReQueryingTheCatalog() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        // PDV-F010: fechar não exige posse da comanda, mas exige caixa aberto de quem fecha —
        // é a gaveta dele que recebe o dinheiro.
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("25.00"))))
                .thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<PaymentCommand> payments = List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null));
        Order order = comandaService.closeComanda(10L, payments, "caixa1");

        assertThat(order.id()).isEqualTo(500L);
        // PDV-F010: o pedido NASCE MESA — o canal é imutável, não vira MESA depois.
        assertThat(order.channel()).isEqualTo(SalesChannel.MESA);
        assertThat(order.comandaId()).isEqualTo(10L);
        assertThat(order.tableLabel()).isEqualTo("Mesa 4");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        // Nunca resolve o preço de novo pelo catálogo — o item já veio precificado da comanda.
        verifyNoInteractions(estoqueUseCase);
        verify(orderPaymentRepository).save(argThat(p -> p.orderId().equals(500L)
                && p.amount().compareTo(new BigDecimal("25.00")) == 0));
        verify(cashbackUseCase).recordEarnedForOrder(any());
        verify(comandaRepository).save(argThat(c -> c.status() == ComandaStatus.FECHADA
                && c.orderId().equals(500L)));
    }

    @Test
    void closeComanda_doesNotAdjustStockAgain() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        // PDV-F010: fechar não exige posse da comanda, mas exige caixa aberto de quem fecha —
        // é a gaveta dele que recebe o dinheiro.
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), "caixa1");

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void closeComanda_refusesEmptyComandaBeforeTouchingOrders() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), "caixa1"))
                .isInstanceOf(ComandaEmptyException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void closeComanda_refusesNonAbertaComanda() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA,
                List.of(essenciaItem()), null, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(fechada));

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verifyNoInteractions(orderRepository);
    }

    // ── Cancelamento ─────────────────────────────────────────────────────────────────────────

    @Test
    void cancelComanda_returnsStockPerItem() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda cancelada = comandaService.cancelComanda(10L, "caixa1");

        assertThat(cancelada.status()).isEqualTo(ComandaStatus.CANCELADA);
        verify(estoqueUseCase).adjustStock(eq("ESS-MENTA"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(BigDecimal.ONE), eq("Cancelamento de comanda #10"), eq("caixa1"));
    }

    @Test
    void cancelComanda_refusesNonAbertaComanda() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(essenciaItem()), 500L, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(fechada));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.cancelComanda(10L, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verifyNoInteractions(estoqueUseCase);
    }

    // ── Leitura ──────────────────────────────────────────────────────────────────────────────

    @Test
    void getComanda_throwsWhenNotFound() {
        when(comandaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comandaService.getComanda(999L)).isInstanceOf(ComandaNotFoundException.class);
    }

    @Test
    void listOpenComandas_delegatesToRepository() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findOpenBySessionId(1L)).thenReturn(List.of(comanda));

        assertThat(comandaService.listOpenComandas(1L)).containsExactly(comanda);
    }

    // ── Sessão de narguilé (PDV-F010) ────────────────────────────────────────────────────────

    /** Sabor "blueberry": variação de R$ 35, num produto cujo open rosh custa R$ 60. */
    private static final Pricing SABOR_BLUE = Pricing.of(new BigDecimal("12.00"), null, new BigDecimal("35.00"));

    private EstoqueUseCase.CatalogSaleInfo sessao(BigDecimal openRoshPrice) {
        return new EstoqueUseCase.CatalogSaleInfo("Sessão de narguilé", SABOR_BLUE, true, true, openRoshPrice);
    }

    private ComandaItem openRoshLancado() {
        return ComandaItem.of(77L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("60.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.OPEN_ROSH,
                false, null);
    }

    /**
     * A armadilha central da feature: a linha chega com o SKU da VARIAÇÃO (R$ 35), mas o open rosh
     * cobra o preço do produto PAI (R$ 60). Resolver pelo SKU, como nos outros modos, cobraria 35.
     */
    @Test
    void addItem_openRoshChargesParentPriceNotVariantPrice() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, "caixa1");

        ComandaItem lancado = updated.items().get(0);
        assertThat(lancado.unitPrice()).isEqualByComparingTo("60.00");
        assertThat(lancado.mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
        // O custo continua vindo do catálogo — o SKU serve para saber o que sai do estoque.
        assertThat(lancado.costPrice()).isEqualByComparingTo("12.00");
    }

    /**
     * Cortesia zera o que se cobra, NUNCA o que se gastou: é o custo congelado que faz a margem do
     * pedido mostrar o prejuízo real da promo — a pergunta de negócio por trás do open rosh.
     */
    @Test
    void addItem_courtesyRecordsZeroPriceButFreezesCostNormally() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda(openRoshLancado())));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, true, 77L, "caixa1");

        ComandaItem troca = updated.items().get(1);
        assertThat(troca.unitPrice()).isEqualByComparingTo("0.00");
        assertThat(troca.costPrice()).isEqualByComparingTo("12.00");
        assertThat(troca.courtesy()).isTrue();
        assertThat(troca.linkedItemId()).isEqualTo(77L);
        // Cortesia baixa estoque igual: o cliente não paga, mas a essência saiu.
        verify(estoqueUseCase).adjustStock(eq("SESS-MENTA"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(BigDecimal.ONE), any(), eq("caixa1"));
    }

    /** TROCA é cortesia por definição — não depende de o cliente HTTP ter marcado o campo. */
    @Test
    void addItem_trocaIsCourtesyEvenWhenClientDidNotFlagIt() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda(openRoshLancado())));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, false, 77L, "caixa1");

        assertThat(updated.items().get(1).courtesy()).isTrue();
        assertThat(updated.items().get(1).unitPrice()).isEqualByComparingTo("0.00");
    }

    /** O segundo sabor sem promo é linha própria, cobrada pelo preço da sua variação. */
    @Test
    void addItem_saborExtraWithoutPromoChargesItsOwnVariantPrice() {
        ComandaItem primeiraLinha = ComandaItem.of(55L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("35.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.NORMAL,
                false, null);
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda(primeiraLinha)));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.SABOR_EXTRA, false, 55L, "caixa1");

        assertThat(updated.items().get(1).unitPrice()).isEqualByComparingTo("35.00");
        assertThat(updated.items().get(1).courtesy()).isFalse();
    }

    @Test
    void addItem_refusesSkuNotAvailableForTableBeforeTouchingStock() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("CIGARRO-01")).thenReturn(
                new EstoqueUseCase.CatalogSaleInfo("Cigarro", ESSENCIA, false, false, null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "CIGARRO-01", BigDecimal.ONE, null, false,
                null, "caixa1"))
                .isInstanceOf(NotAvailableForTableException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void addItem_refusesSessionModeOnProductThatIsNotASession() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA")).thenReturn(
                new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA, true, false, null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, "caixa1"))
                .isInstanceOf(NotASessionProductException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    /** Sem preço de open rosh não há fallback para o preço do sabor — recusa. */
    @Test
    void addItem_refusesOpenRoshOnProductWithoutOpenRoshPrice() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, "caixa1"))
                .isInstanceOf(OpenRoshNotPricedException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addItem_refusesSaborExtraWithoutLinkedItem() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.SABOR_EXTRA, false, null, "caixa1"))
                .isInstanceOf(LinkedItemRequiredException.class);
    }

    /** O id tem que ser de uma linha DESTA comanda — senão uma troca se penduraria na mesa ao lado. */
    @Test
    void addItem_refusesLinkedItemFromAnotherComanda() {
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda(openRoshLancado())));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, true, 999L, "caixa1"))
                .isInstanceOf(LinkedItemRequiredException.class);
    }

    /** Troca cortesia sobre uma sessão comum daria narguilé de graça. */
    @Test
    void addItem_refusesTrocaLinkedToLineThatIsNotOpenRosh() {
        ComandaItem normal = ComandaItem.of(55L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("35.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.NORMAL,
                false, null);
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda(normal)));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, true, 55L, "caixa1"))
                .isInstanceOf(NotAnOpenRoshException.class);
    }

    // ── Fechamento da mesa (PDV-F010) ────────────────────────────────────────────────────────

    /** Sem isto, o histórico da mesa não distingue cortesia de item cobrado. */
    @Test
    void closeComanda_carriesModeAndCourtesyIntoTheOrderItems() {
        ComandaItem cortesia = ComandaItem.of(78L, "SESS-MENTA", BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.TROCA,
                true, 77L);
        when(comandaRepository.findById(10L))
                .thenReturn(Optional.of(abertaComanda(openRoshLancado(), cortesia)));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("60.00"), null)), "caixa1");

        assertThat(order.items().get(0).mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
        assertThat(order.items().get(1).mode()).isEqualTo(ConsumptionMode.TROCA);
        assertThat(order.items().get(1).courtesy()).isTrue();
        // Cortesia entra a zero no líquido, mas com custo — é o que revela o prejuízo da promo.
        assertThat(order.items().get(1).netAmount()).isEqualByComparingTo("0.00");
        assertThat(order.items().get(1).costPrice()).isEqualByComparingTo("12.00");
    }

    /**
     * Decisão do dono: mesas compartilhadas, mas o dinheiro pertence à gaveta que o recebeu. Quando
     * outro atendente fecha a mesa, o pedido entra na sessão DELE — e não na de quem abriu.
     */
    @Test
    void closeComanda_creditsTheSessionOfWhoeverCloses() {
        CashRegisterSession outroCaixa = CashRegisterSession.of(2L, "caixa2", Instant.now(),
                BigDecimal.TEN, "LOJA-01", null, null, null, null, null, CashRegisterSession.Status.OPEN);
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda(essenciaItem())));
        when(pdvService.getCurrentSession("caixa2")).thenReturn(outroCaixa);
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), "caixa2");

        assertThat(order.sessionId()).isEqualTo(2L);
        // O depósito continua sendo o da comanda — é de lá que o estoque saiu, item a item.
        assertThat(order.warehouseCode()).isEqualTo("LOJA-01");
    }

    /** Irmã de COMANDA_EMPTY: mesa só de cortesias não vira pedido de R$ 0. */
    @Test
    void closeComanda_refusesComandaMadeOnlyOfCourtesies() {
        ComandaItem cortesia = ComandaItem.of(78L, "SESS-MENTA", BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.TROCA,
                true, null);
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(abertaComanda(cortesia)));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), "caixa1"))
                .isInstanceOf(ComandaOnlyCourtesyException.class);
        verifyNoInteractions(orderRepository);
    }

    /** O cliente vinculado na abertura chega ao pedido — é o que gera cashback na mesa. */
    @Test
    void closeComanda_carriesTheComandaCustomerIntoTheOrder() {
        Comanda comMesa = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                List.of(essenciaItem()), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comMesa));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), "caixa1");

        assertThat(order.customerId()).isEqualTo(42L);
        verify(cashbackUseCase).recordEarnedForOrder(any());
    }
}
