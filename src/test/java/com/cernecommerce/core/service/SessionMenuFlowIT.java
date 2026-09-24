package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionAssetUnavailableException;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDV-F021 de ponta a ponta contra banco real: cardápio configurado → sessão lançada com vaso
 * grande → utensílios presos → 2º rosh → fechamento → utensílios livres de novo. É o teste que
 * exercita as consultas de disponibilidade e a trava dos tipos de utensílio, que os testes de
 * unidade mockam.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SessionMenuFlowIT {

    @Autowired SessionMenuUseCase sessionMenuUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static int disponivel(SessionMenu menu, String codigo) {
        return menu.utensilios().stream().filter(a -> a.tipo().codigo().equals(codigo)).findFirst()
                .orElseThrow().disponivel();
    }

    @Test
    void sessionWithBigVase_holdsTheUtensilsUntilTheTableCloses() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String vasoP = "VP" + suffix;
        String vasoG = "VG" + suffix;
        String pinca = "PI" + suffix;
        String operator = "caixa-" + suffix;
        String warehouse = "LOUNGE-" + suffix;

        // Cardápio: vaso grande só 1, pinça 2 (inclusa).
        sessionMenuUseCase.createAssetType(vasoP, "Vaso pequeno " + suffix, 2, false);
        sessionMenuUseCase.createAssetType(vasoG, "Vaso grande " + suffix, 1, false);
        SessionAssetType pincaType = sessionMenuUseCase.createAssetType(pinca, "Pinça " + suffix, 2, true);
        sessionMenuUseCase.updateSettings(new SessionSettings(vasoP, vasoG, new BigDecimal("10.00"), Set.of()));
        SessionTier premium = sessionMenuUseCase.createTier("Premium " + suffix, new BigDecimal("30.00"),
                "Luk, Smynar, Nay", 2);
        SessionTier tradicional = sessionMenuUseCase.createTier("Tradicional " + suffix, new BigDecimal("25.00"),
                "Zgy, Zomo, Pred", 1);

        estoqueUseCase.createWarehouse(warehouse, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 4", operator);
        flushAndClear();

        // 1. Sessão Premium com vaso grande: R$ 30 + R$ 10.
        Comanda comSessao = comandaUseCase.addSession(mesa.id(), premium.id(), "Luk Uva", true, operator);
        flushAndClear();
        ComandaItem sessao = comSessao.items().get(0);
        assertThat(sessao.mode()).isEqualTo(ConsumptionMode.SESSAO);
        assertThat(sessao.unitPrice()).isEqualByComparingTo("40.00");
        assertThat(sessao.notes()).isEqualTo("Luk Uva · Vaso grande");

        SessionMenu emUso = sessionMenuUseCase.getMenu();
        assertThat(disponivel(emUso, vasoG)).isZero();
        assertThat(disponivel(emUso, pinca)).isEqualTo(1);
        assertThat(disponivel(emUso, vasoP)).isEqualTo(2);

        // 2. Segunda sessão com vaso grande: o único está na mesa — recusada.
        Comanda outraMesa = comandaUseCase.openComanda(caixa.id(), "Mesa 5", operator);
        assertThatThrownBy(() -> comandaUseCase.addSession(outraMesa.id(), premium.id(), "Nay Menta", true, operator))
                .isInstanceOf(SessionAssetUnavailableException.class);

        // 3. 2º rosh (fora de dia de promoção): cobra a faixa escolhida, sem utensílio novo.
        Comanda comRosh = comandaUseCase.addRoshExtra(mesa.id(), sessao.id(), tradicional.id(), "Pred Menta",
                operator);
        flushAndClear();
        ComandaItem rosh = comRosh.items().stream().filter(i -> i.mode() == ConsumptionMode.ROSH_EXTRA)
                .findFirst().orElseThrow();
        assertThat(rosh.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(rosh.linkedItemId()).isEqualTo(sessao.id());
        assertThat(disponivel(sessionMenuUseCase.getMenu(), pinca)).isEqualTo(1);

        // 4. Fecha: R$ 65, pedido com a essência na nota, utensílios de volta.
        Order order = comandaUseCase.closeComanda(mesa.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("65.00"), null)),
                null, false, null, operator);
        flushAndClear();

        assertThat(order.netAmount()).isEqualByComparingTo("65.00");
        assertThat(order.items()).extracting(i -> i.mode())
                .containsExactlyInAnyOrder(ConsumptionMode.SESSAO, ConsumptionMode.ROSH_EXTRA);
        assertThat(comandaUseCase.getComanda(mesa.id()).status()).isEqualTo(ComandaStatus.FECHADA);
        SessionMenu livre = sessionMenuUseCase.getMenu();
        assertThat(disponivel(livre, vasoG)).isEqualTo(1);
        assertThat(disponivel(livre, pinca)).isEqualTo(pincaType.quantidadeTotal());
    }

    @Test
    void cancellingTheTable_releasesTheUtensils() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String vasoP = "VP" + suffix;
        String operator = "caixa-" + suffix;
        String warehouse = "LOUNGE-" + suffix;

        sessionMenuUseCase.createAssetType(vasoP, "Vaso " + suffix, 1, false);
        sessionMenuUseCase.updateSettings(new SessionSettings(vasoP, null, BigDecimal.ZERO, Set.of()));
        SessionTier tier = sessionMenuUseCase.createTier("Sence " + suffix, new BigDecimal("40.00"), "Sence", 3);
        estoqueUseCase.createWarehouse(warehouse, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 9", operator);

        comandaUseCase.addSession(mesa.id(), tier.id(), "Sence Blue", false, operator);
        flushAndClear();
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isZero();

        comandaUseCase.cancelComanda(mesa.id(), operator);
        flushAndClear();
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isEqualTo(1);
    }
}
