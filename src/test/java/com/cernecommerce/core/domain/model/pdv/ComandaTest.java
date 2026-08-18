package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComandaTest {

    private static ComandaItem item(String sku, String unitPrice) {
        return ComandaItem.of(null, sku, BigDecimal.ONE, new BigDecimal(unitPrice), null, null, Instant.now());
    }

    private static Comanda open() {
        return Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1");
    }

    // ── Abertura ─────────────────────────────────────────────────────────────────────────────

    @Test
    void open_startsAbertaAndEmpty() {
        Comanda comanda = open();

        assertThat(comanda.id()).isNull();
        assertThat(comanda.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(comanda.isOpen()).isTrue();
        assertThat(comanda.items()).isEmpty();
        assertThat(comanda.orderId()).isNull();
        assertThat(comanda.closedAt()).isNull();
        assertThat(comanda.runningTotal()).isEqualByComparingTo("0");
    }

    @Test
    void open_rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> Comanda.open(null, "LOJA-01", "Mesa 4", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sessionId");
        assertThatThrownBy(() -> Comanda.open(1L, " ", "Mesa 4", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("warehouseCode");
        assertThatThrownBy(() -> Comanda.open(1L, "LOJA-01", " ", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tableOrCustomerLabel");
        assertThatThrownBy(() -> Comanda.open(1L, "LOJA-01", "Mesa 4", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("openedBy");
    }

    // ── Acúmulo de itens ─────────────────────────────────────────────────────────────────────

    @Test
    void withAddedItem_accumulatesWithoutMutatingTheOriginal() {
        Comanda comanda = open();
        Comanda withOneItem = comanda.withAddedItem(item("ESS-MENTA", "25.00"));

        assertThat(comanda.items()).isEmpty();
        assertThat(withOneItem.items()).hasSize(1);
        assertThat(withOneItem.runningTotal()).isEqualByComparingTo("25.00");

        Comanda withTwoItems = withOneItem.withAddedItem(item("CARV-001", "15.00"));
        assertThat(withTwoItems.items()).hasSize(2);
        assertThat(withTwoItems.runningTotal()).isEqualByComparingTo("40.00");
    }

    @Test
    void withAddedItem_refusesOnNonAbertaComanda() {
        Comanda closed = open().withAddedItem(item("ESS-MENTA", "25.00")).closed(99L, Instant.now());

        assertThatThrownBy(() -> closed.withAddedItem(item("CARV-001", "15.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Fechamento ───────────────────────────────────────────────────────────────────────────

    @Test
    void closed_stampsOrderIdAndClosedAt() {
        Comanda comanda = open().withAddedItem(item("ESS-MENTA", "25.00"));
        Comanda fechada = comanda.closed(42L, Instant.now());

        assertThat(fechada.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(fechada.orderId()).isEqualTo(42L);
        assertThat(fechada.closedAt()).isNotNull();
        assertThat(fechada.items()).hasSize(1);
    }

    @Test
    void closed_refusesToCloseTwice() {
        Comanda fechada = open().withAddedItem(item("ESS-MENTA", "25.00")).closed(42L, Instant.now());

        assertThatThrownBy(() -> fechada.closed(43L, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closed_requiresOrderId() {
        Comanda comanda = open().withAddedItem(item("ESS-MENTA", "25.00"));

        assertThatThrownBy(() -> comanda.closed(null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orderId");
    }

    // ── Cancelamento ─────────────────────────────────────────────────────────────────────────

    @Test
    void cancelled_hasClosedAtButNoOrderId() {
        Comanda comanda = open().withAddedItem(item("ESS-MENTA", "25.00"));
        Comanda cancelada = comanda.cancelled(Instant.now());

        assertThat(cancelada.status()).isEqualTo(ComandaStatus.CANCELADA);
        assertThat(cancelada.orderId()).isNull();
        assertThat(cancelada.closedAt()).isNotNull();
        // Itens permanecem no registro — é o rastro de que a comanda existiu, mesmo abandonada.
        assertThat(cancelada.items()).hasSize(1);
    }

    @Test
    void cancelled_refusesOnAlreadyClosedComanda() {
        Comanda fechada = open().withAddedItem(item("ESS-MENTA", "25.00")).closed(42L, Instant.now());

        assertThatThrownBy(() -> fechada.cancelled(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Invariantes de reconstituição (espelham o CHECK da V104) ────────────────────────────

    @Test
    void of_rejectsAbertaComandaWithClosedAtOrOrderId() {
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA,
                List.of(), 42L, "caixa1", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsFechadaComandaWithoutOrderIdOrClosedAt() {
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(), null, "caixa1", Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(), 42L, "caixa1", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsCanceladaComandaWithOrderIdOrWithoutClosedAt() {
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA,
                List.of(), 42L, "caixa1", Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA,
                List.of(), null, "caixa1", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant openedAt = Instant.parse("2026-08-18T18:00:00Z");
        Instant closedAt = Instant.parse("2026-08-18T20:00:00Z");
        Comanda comanda = Comanda.of(7L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(item("ESS-MENTA", "25.00")), 99L, "caixa1", openedAt, closedAt);

        assertThat(comanda.id()).isEqualTo(7L);
        assertThat(comanda.orderId()).isEqualTo(99L);
        assertThat(comanda.openedAt()).isEqualTo(openedAt);
        assertThat(comanda.closedAt()).isEqualTo(closedAt);
    }

    @Test
    void itemsAreDefensivelyCopied() {
        List<ComandaItem> items = new java.util.ArrayList<>(List.of(item("ESS-MENTA", "25.00")));
        Comanda comanda = Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA, items, null,
                "caixa1", Instant.now(), null);

        items.add(item("CARV-001", "15.00"));

        assertThat(comanda.items()).hasSize(1);
    }
}
