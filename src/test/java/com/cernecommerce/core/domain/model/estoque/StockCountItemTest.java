package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCountItemTest {

    @Test
    void counted_nasceSemConfrontoComOSistema() {
        StockCountItem item = StockCountItem.counted("NARG-001", new BigDecimal("37.000"));

        assertThat(item.id()).isNull();
        assertThat(item.countedQuantity()).isEqualByComparingTo("37.000");
        assertThat(item.expectedQuantity()).isNull();
        assertThat(item.difference()).isNull();
        assertThat(item.diverges()).as("sem confronto ainda, não há divergência conhecida").isFalse();
    }

    @Test
    void counted_aceitaZero() {
        assertThat(StockCountItem.counted("NARG-001", BigDecimal.ZERO).countedQuantity())
                .isEqualByComparingTo("0");
    }

    @Test
    void throwsWhenCountedQuantityIsNegative() {
        assertThatThrownBy(() -> StockCountItem.counted("NARG-001", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> StockCountItem.counted("  ", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconciledWith_faltaNaPrateleiraDaDiferencaNegativa() {
        StockCountItem item = StockCountItem.counted("NARG-001", new BigDecimal("8.000"))
                .reconciledWith(new BigDecimal("10.000"));

        assertThat(item.expectedQuantity()).isEqualByComparingTo("10.000");
        assertThat(item.difference()).isEqualByComparingTo("-2.000");
        assertThat(item.diverges()).isTrue();
    }

    @Test
    void reconciledWith_sobraDaDiferencaPositiva() {
        StockCountItem item = StockCountItem.counted("NARG-001", new BigDecimal("12.000"))
                .reconciledWith(new BigDecimal("9.000"));

        assertThat(item.difference()).isEqualByComparingTo("3.000");
        assertThat(item.diverges()).isTrue();
    }

    /** Contagem que bateu não gera movimentação no fechamento. */
    @Test
    void reconciledWith_contagemQueBate_naoDiverge() {
        StockCountItem item = StockCountItem.counted("NARG-001", new BigDecimal("5.000"))
                .reconciledWith(new BigDecimal("5.000"));

        assertThat(item.difference()).isEqualByComparingTo("0");
        assertThat(item.diverges()).isFalse();
    }

    @Test
    void reconciledWith_preservaIdESku() {
        StockCountItem item = StockCountItem.of(7L, "NARG-001", new BigDecimal("8.000"), null, null)
                .reconciledWith(new BigDecimal("10.000"));

        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.sku()).isEqualTo("NARG-001");
        assertThat(item.countedQuantity()).isEqualByComparingTo("8.000");
    }

    // ── Lote (EST-F008) ──────────────────────────────────────────────────────────────────────

    @Test
    void counted_semLote_deixaLotCodeNulo() {
        StockCountItem item = StockCountItem.counted("NARG-001", new BigDecimal("5.000"));

        assertThat(item.lotCode()).isNull();
    }

    @Test
    void counted_comLote_preservaLotCode() {
        StockCountItem item = StockCountItem.counted("ESS-001", new BigDecimal("5.000"), "LOTE-A");

        assertThat(item.lotCode()).isEqualTo("LOTE-A");
    }

    @Test
    void of_comLote_preservaLotCode() {
        StockCountItem item = StockCountItem.of(7L, "ESS-001", new BigDecimal("5.000"), null, null, "LOTE-A");

        assertThat(item.lotCode()).isEqualTo("LOTE-A");
    }

    @Test
    void reconciledWith_preservaLotCode() {
        StockCountItem item = StockCountItem.counted("ESS-001", new BigDecimal("5.000"), "LOTE-A")
                .reconciledWith(new BigDecimal("6.000"));

        assertThat(item.lotCode()).isEqualTo("LOTE-A");
        assertThat(item.difference()).isEqualByComparingTo("-1.000");
    }
}
