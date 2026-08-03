package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class StockCountTest {

    @Test
    void open_nasceAbertaVaziaESemFechamento() {
        StockCount count = StockCount.open(1L, "gerente");

        assertThat(count.id()).isNull();
        assertThat(count.status()).isEqualTo(StockCountStatus.ABERTA);
        assertThat(count.isOpen()).isTrue();
        assertThat(count.items()).isEmpty();
        assertThat(count.closedAt()).isNull();
        assertThat(count.createdAt()).isNotNull();
    }

    @Test
    void throwsWhenWarehouseIdIsNull() {
        assertThatThrownBy(() -> StockCount.open(null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenUsernameIsBlank() {
        assertThatThrownBy(() -> StockCount.open(1L, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withCountedItem_acrescentaItem() {
        StockCount count = StockCount.open(1L, "gerente").withCountedItem("A", new BigDecimal("5.000"));

        assertThat(count.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("A");
            assertThat(item.countedQuantity()).isEqualByComparingTo("5.000");
            assertThat(item.expectedQuantity()).isNull();
        });
    }

    /** Recontar é normal num balanço; duas linhas do mesmo SKU tornariam o fechamento ambíguo. */
    @Test
    void withCountedItem_recontarSobrescreveEPreservaAPosicao() {
        StockCount count = StockCount.open(1L, "gerente")
                .withCountedItem("A", new BigDecimal("1.000"))
                .withCountedItem("B", new BigDecimal("2.000"))
                .withCountedItem("A", new BigDecimal("9.000"));

        assertThat(count.items()).hasSize(2);
        assertThat(count.items()).extracting(StockCountItem::sku)
                .as("A não vai para o fim da lista ao ser recontado").containsExactly("A", "B");
        assertThat(count.items().getFirst().countedQuantity()).isEqualByComparingTo("9.000");
    }

    @Test
    void withCountedItem_recontarPreservaOIdDaLinha() {
        StockCount count = StockCount.of(1L, 1L, StockCountStatus.ABERTA, "gerente", Instant.now(), null,
                List.of(StockCountItem.of(7L, "A", new BigDecimal("1.000"), null, null)));

        StockCount recontado = count.withCountedItem("A", new BigDecimal("4.000"));

        assertThat(recontado.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.countedQuantity()).isEqualByComparingTo("4.000");
        });
    }

    @Test
    void withCountedItem_aceitaContagemZero() {
        StockCount count = StockCount.open(1L, "gerente").withCountedItem("A", BigDecimal.ZERO);

        assertThat(count.items().getFirst().countedQuantity()).isEqualByComparingTo("0");
    }

    // ── Contagem por lote (EST-F008) ─────────────────────────────────────────────────────────

    @Test
    void withCountedItem_comLote_acrescentaLinhaComLotCode() {
        StockCount count = StockCount.open(1L, "gerente")
                .withCountedItem("ESS-001", new BigDecimal("3.000"), "LOTE-A");

        assertThat(count.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("ESS-001");
            assertThat(item.lotCode()).isEqualTo("LOTE-A");
            assertThat(item.countedQuantity()).isEqualByComparingTo("3.000");
        });
    }

    /** Cada lote é uma contagem própria: mesmo SKU, lote diferente, é linha nova, não sobrescrita. */
    @Test
    void withCountedItem_mesmoSkuLoteDiferente_acrescentaLinhaNova() {
        StockCount count = StockCount.open(1L, "gerente")
                .withCountedItem("ESS-001", new BigDecimal("3.000"), "LOTE-A")
                .withCountedItem("ESS-001", new BigDecimal("5.000"), "LOTE-B");

        assertThat(count.items()).hasSize(2);
        assertThat(count.items()).extracting(StockCountItem::lotCode).containsExactly("LOTE-A", "LOTE-B");
    }

    /** Recontar o mesmo lote é o caso normal, igual à recontagem por SKU: sobrescreve. */
    @Test
    void withCountedItem_mesmoSkuMesmoLote_sobrescreve() {
        StockCount count = StockCount.open(1L, "gerente")
                .withCountedItem("ESS-001", new BigDecimal("3.000"), "LOTE-A")
                .withCountedItem("ESS-001", new BigDecimal("5.000"), "LOTE-B")
                .withCountedItem("ESS-001", new BigDecimal("9.000"), "LOTE-A");

        assertThat(count.items()).hasSize(2);
        assertThat(count.items()).extracting(StockCountItem::lotCode, StockCountItem::countedQuantity)
                .containsExactly(
                        tuple("LOTE-A", new BigDecimal("9.000")),
                        tuple("LOTE-B", new BigDecimal("5.000")));
    }

    @Test
    void closed_marcaFechadaECarimbaOInstante() {
        StockCount closed = StockCount.open(1L, "gerente").closed();

        assertThat(closed.status()).isEqualTo(StockCountStatus.FECHADA);
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    void cancelled_marcaCanceladaECarimbaOInstante() {
        StockCount cancelled = StockCount.open(1L, "gerente").cancelled();

        assertThat(cancelled.status()).isEqualTo(StockCountStatus.CANCELADA);
        assertThat(cancelled.isOpen()).isFalse();
        assertThat(cancelled.closedAt()).isNotNull();
    }

    @Test
    void itemsSaoCopiadosDefensivamente() {
        List<StockCountItem> mutable = new java.util.ArrayList<>();
        mutable.add(StockCountItem.counted("A", BigDecimal.ONE));

        StockCount count = StockCount.of(1L, 1L, StockCountStatus.ABERTA, "gerente",
                Instant.now(), null, mutable);
        mutable.clear();

        assertThat(count.items()).hasSize(1);
    }
}
