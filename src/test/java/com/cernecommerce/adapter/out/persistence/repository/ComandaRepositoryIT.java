package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.in.CrmUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o adapter de persistência de comandas contra banco real (PDV-F009).
 *
 * <p>Mesma razão de {@code PedidoRepositoryIT}: a suíte de unidade mocka {@code ComandaRepository},
 * então nada exercitava o mapeamento domínio↔entidade de {@code comanda}/{@code comanda_item} nem a
 * query de "comandas abertas da sessão".</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ComandaRepositoryIT {

    @Autowired ComandaRepositoryImpl comandaRepository;
    @Autowired CrmUseCase crmUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static ComandaItem essenciaItem() {
        return ComandaItem.of(null, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now());
    }

    private static ComandaItem sessionItem(String sku, String unitPrice, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId) {
        return ComandaItem.of(null, sku, BigDecimal.ONE, new BigDecimal(unitPrice),
                new BigDecimal("10.00"), "Sessão " + sku, Instant.now(), mode, courtesy, linkedItemId);
    }

    @Test
    void save_persistsAndReloadsAnOpenComanda() {
        Comanda saved = comandaRepository.save(Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1"));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.sessionId()).isEqualTo(1L);
        assertThat(reloaded.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(reloaded.tableOrCustomerLabel()).isEqualTo("Mesa 4");
        assertThat(reloaded.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(reloaded.openedBy()).isEqualTo("caixa1");
        assertThat(reloaded.items()).isEmpty();
    }

    @Test
    void save_roundTripsAccumulatedItemsWithFrozenPrices() {
        Comanda comanda = Comanda.open(2L, "LOJA-01", "Mesa 5", "caixa1")
                .withAddedItem(essenciaItem());
        Comanda saved = comandaRepository.save(comanda);
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("ESS-MENTA");
            assertThat(item.quantity()).isEqualByComparingTo("1");
            assertThat(item.unitPrice()).isEqualByComparingTo("25.00");
            assertThat(item.costPrice()).isEqualByComparingTo("10.00");
            assertThat(item.productName()).isEqualTo("Essência Menta");
            assertThat(item.addedAt()).isNotNull();
        });
        assertThat(reloaded.runningTotal()).isEqualByComparingTo("25.00");
    }

    @Test
    void findOpenBySessionId_returnsOnlyAbertaComandas() {
        Comanda aberta = comandaRepository.save(Comanda.open(3L, "LOJA-01", "Mesa 1", "caixa1"));
        Comanda fechada = comandaRepository.save(Comanda.open(3L, "LOJA-01", "Mesa 2", "caixa1")
                .withAddedItem(essenciaItem()));
        comandaRepository.save(fechada.closed(999L, Instant.now()));
        flushAndClear();

        List<Comanda> abertas = comandaRepository.findOpenBySessionId(3L);

        assertThat(abertas).extracting(Comanda::id).containsExactly(aberta.id());
    }

    @Test
    void save_transitionsToFechadaAndPersistsOrderId() {
        Comanda comanda = comandaRepository.save(Comanda.open(4L, "LOJA-01", "Mesa 6", "caixa1")
                .withAddedItem(essenciaItem()));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();

        Comanda fechada = comandaRepository.save(comanda.closed(777L, Instant.now()));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(fechada.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(reloaded.orderId()).isEqualTo(777L);
        assertThat(reloaded.closedAt()).isNotNull();
    }

    @Test
    void save_transitionsToCanceladaAndPersistsClosedAtWithoutOrderId() {
        Comanda comanda = comandaRepository.save(Comanda.open(5L, "LOJA-01", "Mesa 7", "caixa1")
                .withAddedItem(essenciaItem()));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();

        Comanda cancelada = comandaRepository.save(comanda.cancelled(Instant.now()));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(cancelada.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ComandaStatus.CANCELADA);
        assertThat(reloaded.orderId()).isNull();
        assertThat(reloaded.closedAt()).isNotNull();
        // Itens permanecem: é o rastro de que a comanda existiu, mesmo abandonada.
        assertThat(reloaded.items()).hasSize(1);
    }

    @Test
    void save_roundTripsSessionFieldsAndCustomer() {
        // customer_id tem FK para customers(id) — não dá para inventar um número aqui.
        Customer cliente = crmUseCase.createCustomer("Cliente Mesa", "11999990000", null, null, "mesa");

        Comanda comanda = Comanda.open(6L, "LOJA-01", "Mesa 8", cliente.id(), "caixa1")
                .withAddedItem(sessionItem("SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null));
        Comanda saved = comandaRepository.save(comanda);
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.customerId()).isEqualTo(cliente.id());
        assertThat(reloaded.items()).singleElement().satisfies(item -> {
            assertThat(item.mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
            assertThat(item.courtesy()).isFalse();
            assertThat(item.linkedItemId()).isNull();
            // Custo congelado mesmo com o preço vindo de fora do SKU — é ele que faz a margem
            // mostrar o prejuízo real do open rosh.
            assertThat(item.costPrice()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void save_roundTripsCourtesyLineWithFrozenCost() {
        Comanda comanda = comandaRepository.save(Comanda.open(7L, "LOJA-01", "Mesa 10", "caixa1")
                .withAddedItem(sessionItem("SESS-MENTA", "25.00", ConsumptionMode.NORMAL, false, null)));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();
        Long sessaoId = comanda.items().get(0).id();

        Comanda saved = comandaRepository.save(comanda.withAddedItem(
                sessionItem("SESS-UVA", "0.00", ConsumptionMode.SABOR_EXTRA, true, sessaoId)));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.items()).hasSize(2);
        assertThat(reloaded.items().get(1)).satisfies(extra -> {
            assertThat(extra.mode()).isEqualTo(ConsumptionMode.SABOR_EXTRA);
            assertThat(extra.courtesy()).isTrue();
            assertThat(extra.unitPrice()).isEqualByComparingTo("0.00");
            // Cortesia não é linha grátis para a contabilidade: o custo continua congelado.
            assertThat(extra.costPrice()).isEqualByComparingTo("10.00");
            assertThat(extra.linkedItemId()).isEqualTo(sessaoId);
        });
        // Só a linha cobrada entra no total.
        assertThat(reloaded.runningTotal()).isEqualByComparingTo("25.00");
    }

    /**
     * Regressão do apaga-e-reinsere: {@code save} reescrevia a lista inteira de itens a cada
     * lançamento, dando id novo a cada linha. Desde a V114 isso quebra de duas formas — a FK
     * {@code linked_item_id → comanda_item(id)} passa a apontar para uma linha recém-deletada, e o
     * id que o cliente recebeu para mandar de volta no {@code TROCA} morre no lançamento seguinte.
     */
    @Test
    void save_keepsItemIdsStableAcrossLaunches_soLinkedItemIdSurvives() {
        Comanda comanda = comandaRepository.save(Comanda.open(8L, "LOJA-01", "Mesa 11", "caixa1")
                .withAddedItem(sessionItem("SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null)));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();
        Long openRoshId = comanda.items().get(0).id();

        // Troca pendurada no open rosh — é aqui que a FK estourava.
        comanda = comandaRepository.save(comanda.withAddedItem(
                sessionItem("SESS-UVA", "0.00", ConsumptionMode.TROCA, true, openRoshId)));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();
        assertThat(comanda.items().get(0).id()).isEqualTo(openRoshId);

        // E um terceiro lançamento qualquer não pode mexer nos ids das duas linhas anteriores,
        // ou o cliente ficaria com um linkedItemId morto na mão.
        Long trocaId = comanda.items().get(1).id();
        Comanda saved = comandaRepository.save(comanda.withAddedItem(essenciaItem()));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.items()).extracting(ComandaItem::id)
                .containsExactly(openRoshId, trocaId, reloaded.items().get(2).id());
        assertThat(reloaded.items().get(1).linkedItemId()).isEqualTo(openRoshId);
    }
}
