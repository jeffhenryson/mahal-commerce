package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockLotTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 12, 31);

    @Test
    void create_buildsLotWithoutIdAndZeroQuantity() {
        StockLot lot = StockLot.create("ESS-001", 1L, "LOTE-A", EXPIRY);

        assertThat(lot.id()).isNull();
        assertThat(lot.sku()).isEqualTo("ESS-001");
        assertThat(lot.warehouseId()).isEqualTo(1L);
        assertThat(lot.lotCode()).isEqualTo("LOTE-A");
        assertThat(lot.expiryDate()).isEqualTo(EXPIRY);
        assertThat(lot.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lot.alertedAt()).isNull();
        assertThat(lot.version()).isZero();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant alertedAt = Instant.parse("2026-07-01T00:00:00Z");
        StockLot lot = StockLot.of(5L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("12.500"), alertedAt, 3L);

        assertThat(lot.id()).isEqualTo(5L);
        assertThat(lot.quantity()).isEqualByComparingTo("12.500");
        assertThat(lot.alertedAt()).isEqualTo(alertedAt);
        assertThat(lot.version()).isEqualTo(3L);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> StockLot.create("  ", 1L, "LOTE-A", EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseIdIsNull() {
        assertThatThrownBy(() -> StockLot.create("ESS-001", null, "LOTE-A", EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenLotCodeIsBlank() {
        assertThatThrownBy(() -> StockLot.create("ESS-001", 1L, "  ", EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenExpiryDateIsNull() {
        assertThatThrownBy(() -> StockLot.create("ESS-001", 1L, "LOTE-A", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityIsNegative() {
        assertThatThrownBy(() -> StockLot.of(null, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("-1"), null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receive_addsToQuantityAndKeepsIdentity() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("5.000"), null, 3L);

        StockLot result = lot.receive(new BigDecimal("2.000"));

        assertThat(result.quantity()).isEqualByComparingTo("7.000");
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.version()).isEqualTo(3L);
    }

    @Test
    void receive_zeroOuNegativo_lancaIllegalArgument() {
        StockLot lot = StockLot.create("ESS-001", 1L, "LOTE-A", EXPIRY);

        assertThatThrownBy(() -> lot.receive(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lot.receive(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consume_subtractsFromQuantity() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("5.000"), null, 0L);

        StockLot result = lot.consume(new BigDecimal("2.000"));

        assertThat(result.quantity()).isEqualByComparingTo("3.000");
    }

    @Test
    void consume_ateExatamenteAQuantidade_ehPermitido() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("5.000"), null, 0L);

        StockLot result = lot.consume(new BigDecimal("5.000"));

        assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void consume_maisDoQueTemNoLote_lancaInsufficientStock() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("2.000"), null, 0L);

        assertThatThrownBy(() -> lot.consume(new BigDecimal("5.000")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void consume_zeroOuNegativo_lancaIllegalArgument() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("5.000"), null, 0L);

        assertThatThrownBy(() -> lot.consume(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lot.consume(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconciledTo_substituiAQuantidadeParaCimaOuParaBaixo() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("5.000"), null, 0L);

        assertThat(lot.reconciledTo(new BigDecimal("8.000")).quantity()).isEqualByComparingTo("8.000");
        assertThat(lot.reconciledTo(new BigDecimal("1.000")).quantity()).isEqualByComparingTo("1.000");
        assertThat(lot.reconciledTo(BigDecimal.ZERO).quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reconciledTo_negativo_lancaIllegalArgument() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("5.000"), null, 0L);

        assertThatThrownBy(() -> lot.reconciledTo(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alerted_carimbaAlertedAtEPreservaOResto() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", EXPIRY, new BigDecimal("5.000"), null, 2L);

        StockLot result = lot.alerted();

        assertThat(result.alertedAt()).isNotNull();
        assertThat(result.alreadyAlerted()).isTrue();
        assertThat(result.quantity()).isEqualByComparingTo("5.000");
        assertThat(result.version()).isEqualTo(2L);
    }

    @Test
    void alreadyAlerted_falseQuandoAlertedAtEhNulo() {
        StockLot lot = StockLot.create("ESS-001", 1L, "LOTE-A", EXPIRY);

        assertThat(lot.alreadyAlerted()).isFalse();
    }

    @Test
    void isExpiringBy_incluiVencimentoExatoEVencido() {
        StockLot lot = StockLot.create("ESS-001", 1L, "LOTE-A", LocalDate.of(2026, 8, 1));

        assertThat(lot.isExpiringBy(LocalDate.of(2026, 8, 1))).as("vence exatamente no cutoff").isTrue();
        assertThat(lot.isExpiringBy(LocalDate.of(2026, 9, 1))).as("já vencido antes do cutoff").isTrue();
        assertThat(lot.isExpiringBy(LocalDate.of(2026, 7, 1))).as("ainda não chegou na janela").isFalse();
    }
}
