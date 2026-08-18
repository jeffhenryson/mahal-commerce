package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
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

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static ComandaItem essenciaItem() {
        return ComandaItem.of(null, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now());
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
}
