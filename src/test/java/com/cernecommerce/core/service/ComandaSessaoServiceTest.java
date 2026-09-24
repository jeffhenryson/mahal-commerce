package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.LegacySessionDisabledException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemIsChargedException;
import com.cernecommerce.core.domain.exception.pdv.NotASessionLineException;
import com.cernecommerce.core.domain.exception.pdv.SessionAssetUnavailableException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.out.user.UserRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PDV-F021 — sessão do cardápio na comanda: preço pela faixa, upgrade de vaso, duplo rosh,
 * alocação/liberação de utensílio e a sessão por produto desligada (como em produção).
 */
@ExtendWith(MockitoExtension.class)
class ComandaSessaoServiceTest {

    @Mock ComandaRepository comandaRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock PdvService pdvService;
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;
    @Mock KitBuilderUseCase kitBuilderUseCase;
    @Mock SessionMenuService sessionMenu;

    ComandaService comandaService;

    private static final SessionTier PREMIUM = new SessionTier(2L, "Premium", new BigDecimal("30.00"),
            "Luk, Smynar, Nay", 2, true);
    private static final SessionTier TRADICIONAL = new SessionTier(1L, "Tradicional", new BigDecimal("25.00"),
            "Zgy, Zomo, Pred", 1, true);
    private static final SessionSettings SETTINGS = new SessionSettings("VASO_P", "VASO_G",
            new BigDecimal("10.00"), Set.of(DayOfWeek.WEDNESDAY));
    private static final List<SessionAssetType> KIT_PADRAO = List.of(
            new SessionAssetType(1L, "VASO_P", "Vaso pequeno", 5, false, true),
            new SessionAssetType(3L, "PINCA", "Pinça", 5, true, true));

    @BeforeEach
    void setUp() {
        comandaService = new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService, notificationUseCase, userRepository,
                BigDecimal.TEN, kitBuilderUseCase, sessionMenu, false);
    }

    private static Comanda comanda(ComandaItem... items) {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.ABERTA, List.of(items), null,
                "caixa1", Instant.now(), null);
    }

    private static ComandaItem sessao(Long id, SessionTier tier) {
        return ComandaItem.of(id, tier.sku(), BigDecimal.ONE, tier.preco(), null, "Sessão " + tier.nome(),
                Instant.now(), ConsumptionMode.SESSAO, false, null, "Zomo Blueberry", null);
    }

    private static ComandaItem rosh(Long id, Long parentId, BigDecimal price, boolean courtesy) {
        return ComandaItem.of(id, TRADICIONAL.sku(), BigDecimal.ONE, price, null, "2º rosh Tradicional",
                Instant.now(), ConsumptionMode.ROSH_EXTRA, courtesy, parentId, "Pred Menta", null);
    }

    /** O save falso dá id às linhas novas, como o banco faria. */
    private void givenSaveAssignsItemIds() {
        when(comandaRepository.save(any())).thenAnswer(inv -> {
            Comanda c = inv.getArgument(0);
            List<ComandaItem> items = new ArrayList<>();
            long next = 100;
            for (ComandaItem i : c.items()) {
                items.add(i.id() != null ? i : ComandaItem.of(next++, i.sku(), i.quantity(), i.unitPrice(),
                        i.costPrice(), i.productName(), i.addedAt(), i.mode(), i.courtesy(), i.linkedItemId(),
                        i.notes(), i.surchargeAmount()));
            }
            return Comanda.of(c.id(), c.sessionId(), c.warehouseCode(), c.tableOrCustomerLabel(), c.customerId(),
                    c.status(), items, c.orderId(), c.openedBy(), c.openedAt(), c.closedAt());
        });
    }

    // ── Lançamento da sessão ─────────────────────────────────────────────────────────────────

    @Test
    void addSession_chargesTheTierPrice_allocatesUtensils_andNeverTouchesStock() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, false)).thenReturn(KIT_PADRAO);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addSession(10L, 2L, " Zomo Blueberry ", false, "caixa1");

        ComandaItem linha = result.items().get(0);
        assertThat(linha.mode()).isEqualTo(ConsumptionMode.SESSAO);
        assertThat(linha.sku()).isEqualTo("SESS-2");
        assertThat(linha.unitPrice()).isEqualByComparingTo("30.00");
        assertThat(linha.notes()).isEqualTo("Zomo Blueberry");
        assertThat(linha.costPrice()).isNull();
        verify(sessionMenu).allocate(100L, KIT_PADRAO);
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void addSession_withBigVase_addsTheUpgradeToThePrice() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, true)).thenReturn(KIT_PADRAO);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addSession(10L, 2L, "Luk Uva", true, "caixa1");

        ComandaItem linha = result.items().get(0);
        assertThat(linha.unitPrice()).isEqualByComparingTo("40.00");
        assertThat(linha.notes()).isEqualTo("Luk Uva · Vaso grande");
        assertThat(linha.productName()).contains("vaso grande");
    }

    @Test
    void addSession_withoutFreeUtensil_isRefusedBeforeSaving() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, false))
                .thenThrow(new SessionAssetUnavailableException("VASO_P", "Vaso pequeno", 5));

        assertThatThrownBy(() -> comandaService.addSession(10L, 2L, "Zomo", false, "caixa1"))
                .isInstanceOf(SessionAssetUnavailableException.class);
        verify(comandaRepository, never()).save(any());
        verify(sessionMenu, never()).allocate(any(), any());
    }

    @Test
    void addSession_withoutEssence_isRefused() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);

        assertThatThrownBy(() -> comandaService.addSession(10L, 2L, "  ", false, "caixa1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Duplo rosh ───────────────────────────────────────────────────────────────────────────

    @Test
    void addRoshExtra_onPromoDay_isFreeAndLinkedToTheSession_withoutNewUtensils() {
        Comanda comanda = comanda(sessao(1L, PREMIUM));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.isDuploRoshDay(SETTINGS, comanda.openedAt())).thenReturn(true);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addRoshExtra(10L, 1L, null, "Nay Morango", "caixa1");

        ComandaItem rosh = result.items().get(1);
        assertThat(rosh.mode()).isEqualTo(ConsumptionMode.ROSH_EXTRA);
        assertThat(rosh.linkedItemId()).isEqualTo(1L);
        assertThat(rosh.unitPrice()).isEqualByComparingTo("0");
        assertThat(rosh.courtesy()).isTrue();
        assertThat(rosh.productName()).contains("duplo rosh");
        verify(sessionMenu, never()).reserveAssetsForSession(any(), anyBoolean());
        verify(sessionMenu, never()).allocate(any(), any());
    }

    @Test
    void addRoshExtra_outsidePromo_chargesTheChosenTier() {
        Comanda comanda = comanda(sessao(1L, PREMIUM));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(sessionMenu.requireActiveTier(1L)).thenReturn(TRADICIONAL);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.isDuploRoshDay(SETTINGS, comanda.openedAt())).thenReturn(false);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addRoshExtra(10L, 1L, 1L, "Pred Menta", "caixa1");

        ComandaItem rosh = result.items().get(1);
        assertThat(rosh.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(rosh.courtesy()).isFalse();
        assertThat(rosh.sku()).isEqualTo("SESS-1");
    }

    /** A promoção é UM rosh grátis por sessão; o terceiro rosh da mesma sessão é cobrado. */
    @Test
    void addRoshExtra_secondExtraOnPromoDay_isCharged() {
        Comanda comanda = comanda(sessao(1L, PREMIUM), rosh(2L, 1L, BigDecimal.ZERO, true));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addRoshExtra(10L, 1L, null, "Sence Menta", "caixa1");

        assertThat(result.items().get(2).unitPrice()).isEqualByComparingTo("30.00");
        assertThat(result.items().get(2).courtesy()).isFalse();
    }

    @Test
    void addRoshExtra_onALineThatIsNotASession_isRefused() {
        ComandaItem bebida = ComandaItem.of(1L, "BEB-COLA", BigDecimal.ONE, new BigDecimal("8.00"),
                new BigDecimal("3.00"), "Refrigerante", Instant.now());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(bebida)));

        assertThatThrownBy(() -> comandaService.addRoshExtra(10L, 1L, null, "Zomo", "caixa1"))
                .isInstanceOf(NotASessionLineException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Liberação dos utensílios ─────────────────────────────────────────────────────────────

    @Test
    void removeItem_session_releasesUtensils_withoutStockMovement() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(sessao(1L, PREMIUM))));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.removeItem(10L, 1L, "caixa1");

        verify(sessionMenu).release(List.of(1L));
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void removeItem_sessionWithRoshExtra_isRefusedUntilTheRoshIsRemoved() {
        when(comandaRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(comanda(sessao(1L, PREMIUM), rosh(2L, 1L, new BigDecimal("25.00"), false))));

        assertThatThrownBy(() -> comandaService.removeItem(10L, 1L, "caixa1"))
                .isInstanceOf(LinkedItemIsChargedException.class);
        verify(sessionMenu, never()).release(any());
    }

    @Test
    void cancelComanda_releasesTheSessionUtensils_withoutStockMovement() {
        when(comandaRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(comanda(sessao(1L, PREMIUM), rosh(2L, 1L, BigDecimal.ZERO, true))));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.cancelComanda(10L, "caixa1");

        verify(sessionMenu).release(List.of(1L));
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void closeComanda_withSession_resolvesCashbackByCategory_andReleasesUtensils() {
        Comanda comanda = comanda(sessao(1L, PREMIUM), rosh(2L, 1L, BigDecimal.ZERO, true));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(CashRegisterSession.of(1L, "caixa1",
                Instant.now(), BigDecimal.TEN, "LOJA-01", null, null, null, null, null,
                CashRegisterSession.Status.OPEN));
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return Order.of(500L, o.orderNumber(), o.channel(), o.status(), o.customerId(), o.sessionId(),
                    o.warehouseCode(), o.items(), o.grossAmount(), o.discountAmount(), o.cashbackRedeemed(),
                    o.netAmount(), o.changeAmount(), o.cancelReason(), o.createdAt(), o.paidAt(), o.concludedAt(),
                    o.cancelledAt(), o.refundedAt(), o.reservedAt(), o.separatedAt(), o.shippedAt(),
                    o.deliveredAt(), o.version(), o.comandaId(), o.tableLabel(), o.serviceFeeAmount());
        });
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("30.00"), null)),
                null, false, "caixa1");

        assertThat(order.netAmount()).isEqualByComparingTo("30.00");
        assertThat(order.items()).extracting(i -> i.mode())
                .containsExactly(ConsumptionMode.SESSAO, ConsumptionMode.ROSH_EXTRA);
        assertThat(order.items().get(0).notes()).isEqualTo("Zomo Blueberry");
        verify(cashbackUseCase).resolveApplicableRate("SESS-2", ComandaService.SESSION_CASHBACK_CATEGORY);
        verify(cashbackUseCase, never()).resolveApplicableRate("SESS-2");
        verify(sessionMenu).release(List.of(1L));
        verify(comandaRepository).save(argThat(c -> c.status() == ComandaStatus.FECHADA));
        verifyNoInteractions(estoqueUseCase);
    }

    // ── Sessão por produto desligada ─────────────────────────────────────────────────────────

    @Test
    void addItem_legacySessionMode_isRefusedWhenLegacyIsDisabled() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        when(estoqueUseCase.resolveSaleInfo("ESS-ZGY-BLUE")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Essência Zgy Blueberry", Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")),
                true, true, new BigDecimal("60.00"), 5, false));

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-ZGY-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, null, null, "caixa1"))
                .isInstanceOf(LegacySessionDisabledException.class);
        // Sessão de produto com modo NORMAL também não passa: seria uma sessão cobrada pelo preço do SKU.
        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-ZGY-BLUE", BigDecimal.ONE,
                ConsumptionMode.NORMAL, false, null, null, null, "caixa1"))
                .isInstanceOf(LegacySessionDisabledException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(estoqueUseCase, never()).consumeSession(any(), any(), any(), any());
    }

    @Test
    void addItem_plainTableItem_stillWorksWithLegacyDisabled() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        when(estoqueUseCase.resolveSaleInfo("BEB-COLA")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Refrigerante", Pricing.of(new BigDecimal("3.00"), null, new BigDecimal("8.00"))));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda result = comandaService.addItem(10L, "BEB-COLA", BigDecimal.ONE, ConsumptionMode.NORMAL, false,
                null, null, null, "caixa1");

        assertThat(result.items()).hasSize(1);
        verify(estoqueUseCase).adjustStock(eq("BEB-COLA"), eq("LOJA-01"), any(), eq(BigDecimal.ONE), any(), eq("caixa1"));
    }

    @Test
    void addItem_menuSessionMode_isRefused() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-2", BigDecimal.ONE, ConsumptionMode.SESSAO,
                false, null, null, null, "caixa1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(estoqueUseCase);
    }
}
