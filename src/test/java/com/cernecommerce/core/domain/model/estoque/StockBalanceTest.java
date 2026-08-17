package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ReservedStockException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockBalanceTest {

    @Test
    void zero_buildsBalanceWithoutIdAndZeroQuantity() {
        StockBalance balance = StockBalance.zero("NARG-001", 1L);

        assertThat(balance.id()).isNull();
        assertThat(balance.sku()).isEqualTo("NARG-001");
        assertThat(balance.warehouseId()).isEqualTo(1L);
        assertThat(balance.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.version()).isZero();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        StockBalance balance = StockBalance.of(5L, "NARG-001", 1L, new BigDecimal("12.500"), 3L);

        assertThat(balance.id()).isEqualTo(5L);
        assertThat(balance.quantity()).isEqualByComparingTo("12.500");
        assertThat(balance.version()).isEqualTo(3L);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> StockBalance.zero("  ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseIdIsNull() {
        assertThatThrownBy(() -> StockBalance.of(null, "NARG-001", null, BigDecimal.ZERO, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityIsNegative() {
        assertThatThrownBy(() -> StockBalance.of(null, "NARG-001", 1L, new BigDecimal("-1"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apply_entrada_increasesQuantityAndKeepsVersion() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 3L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("2.000"));

        assertThat(result.quantity()).isEqualByComparingTo("7.000");
        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void apply_saida_decreasesQuantity() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        StockBalance result = balance.apply(MovementType.SAIDA, new BigDecimal("2.000"));

        assertThat(result.quantity()).isEqualByComparingTo("3.000");
    }

    @Test
    void apply_saida_allowsDrainingExactlyToZero() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        StockBalance result = balance.apply(MovementType.SAIDA, new BigDecimal("5.000"));

        assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void apply_saida_throwsInsufficientStockExceptionWhenNotEnoughBalance() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("2.000"), 0L);

        assertThatThrownBy(() -> balance.apply(MovementType.SAIDA, new BigDecimal("5.000")))
                .isInstanceOf(InsufficientStockException.class);
    }

    // EST-C009: AJUSTE deixou de ser delta e passou a ser o saldo contado na prateleira.

    @Test
    void apply_ajuste_substituiOSaldoParaCima() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("8.000"));

        assertThat(result.quantity()).as("vira o alvo, não 5 + 8").isEqualByComparingTo("8.000");
    }

    /** É o caso que o EST-C009 existia para resolver: antes só dava para baixar com SAIDA falsa. */
    @Test
    void apply_ajuste_substituiOSaldoParaBaixo() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("3.000"));

        assertThat(result.quantity()).isEqualByComparingTo("3.000");
    }

    @Test
    void apply_ajuste_paraZeroEhValido() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        assertThat(balance.apply(MovementType.AJUSTE, BigDecimal.ZERO).quantity())
                .isEqualByComparingTo("0.000");
    }

    /** Baixar por AJUSTE nunca é "saldo insuficiente" — é substituição, não subtração. */
    @Test
    void apply_ajuste_abaixoDoSaldoAtual_naoLancaInsufficientStock() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("2.000"), 0L);

        assertThat(balance.apply(MovementType.AJUSTE, new BigDecimal("50.000")).quantity())
                .isEqualByComparingTo("50.000");
        assertThat(balance.apply(MovementType.AJUSTE, new BigDecimal("1.000")).quantity())
                .isEqualByComparingTo("1.000");
    }

    @Test
    void apply_ajuste_negativo_lancaIllegalArgument() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        assertThatThrownBy(() -> balance.apply(MovementType.AJUSTE, new BigDecimal("-1.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apply_ajuste_preservaIdSkuEVersion() {
        StockBalance balance = StockBalance.of(7L, "NARG-001", 3L, new BigDecimal("5.000"), 4L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("9.000"));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.warehouseId()).isEqualTo(3L);
        assertThat(result.version()).as("version preservado para o optimistic locking").isEqualTo(4L);
    }

    // ── Reserva de estoque (EST-F021) ────────────────────────────────────────────────────────

    @Test
    void of_semReservedQuantity_defaultaParaZero() {
        StockBalance balance = StockBalance.of(5L, "NARG-001", 1L, new BigDecimal("12.500"), 3L);

        assertThat(balance.reservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void of_comReservedQuantity_reconstituiDoPersistido() {
        StockBalance balance = StockBalance.of(5L, "NARG-001", 1L, new BigDecimal("12.500"),
                new BigDecimal("4.000"), 3L);

        assertThat(balance.quantity()).isEqualByComparingTo("12.500");
        assertThat(balance.reservedQuantity()).isEqualByComparingTo("4.000");
    }

    @Test
    void construtor_reservedQuantityNulo_defaultaParaZero() {
        StockBalance balance = new StockBalance(1L, "NARG-001", 1L, new BigDecimal("5.000"), null, null, 0L);

        assertThat(balance.reservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void throwsWhenReservedQuantityEhNegativa() {
        assertThatThrownBy(() -> StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("-1.000"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenReservedQuantityMaiorQueQuantity() {
        assertThatThrownBy(() -> StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("5.001"), 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não se reserva o que não existe");
    }

    @Test
    void reservedQuantityIgualQuantity_ehPermitido() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("5.000"), 0L);

        assertThat(balance.availableQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void derived_temReservedQuantitySempreZero() {
        StockBalance balance = StockBalance.derived("KIT-001", 1L, new BigDecimal("3.000"));

        assertThat(balance.reservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void availableQuantity_subtraiReservadoDoFisico() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        assertThat(balance.availableQuantity()).isEqualByComparingTo("6.000");
    }

    @Test
    void availableQuantity_semReserva_igualAoFisico() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        assertThat(balance.availableQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    void reserve_incrementaReservedQuantitySemMexerNoFisico() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 3L);

        StockBalance result = balance.reserve(new BigDecimal("3.000"));

        assertThat(result.quantity()).as("físico não muda na reserva").isEqualByComparingTo("10.000");
        assertThat(result.reservedQuantity()).isEqualByComparingTo("5.000");
        assertThat(result.version()).isEqualTo(3L);
    }

    @Test
    void reserve_ateExatamenteODisponivel_ehPermitido() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        StockBalance result = balance.reserve(new BigDecimal("6.000"));

        assertThat(result.availableQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reserve_acimaDoDisponivel_lancaInsufficientStock_comMensagemDoDisponivel() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        assertThatThrownBy(() -> balance.reserve(new BigDecimal("7.000")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void reserve_quantidadeZero_lancaIllegalArgument() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        assertThatThrownBy(() -> balance.reserve(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserve_quantidadeNegativa_lancaIllegalArgument() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        assertThatThrownBy(() -> balance.reserve(new BigDecimal("-1.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseReservation_devolveAoDisponivelSemMexerNoFisico() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("5.000"), 2L);

        StockBalance result = balance.releaseReservation(new BigDecimal("2.000"));

        assertThat(result.quantity()).as("físico não muda na liberação").isEqualByComparingTo("10.000");
        assertThat(result.reservedQuantity()).isEqualByComparingTo("3.000");
    }

    @Test
    void releaseReservation_maiorQueOReservado_lancaIllegalArgument() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);

        assertThatThrownBy(() -> balance.releaseReservation(new BigDecimal("3.000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("liberação maior que o reservado");
    }

    @Test
    void releaseReservation_quantidadeZeroOuNegativa_lancaIllegalArgument() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);

        assertThatThrownBy(() -> balance.releaseReservation(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.releaseReservation(new BigDecimal("-1.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeReservation_baixaFisicoEReservadoJuntos_disponivelNaoMuda() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 2L);
        BigDecimal availableBefore = balance.availableQuantity();

        StockBalance result = balance.consumeReservation(new BigDecimal("4.000"));

        assertThat(result.quantity()).isEqualByComparingTo("6.000");
        assertThat(result.reservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.availableQuantity()).as("já estava descontado desde a reserva")
                .isEqualByComparingTo(availableBefore);
    }

    @Test
    void consumeReservation_parcial_baixaSoAQuantidadeConsumida() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        StockBalance result = balance.consumeReservation(new BigDecimal("1.500"));

        assertThat(result.quantity()).isEqualByComparingTo("8.500");
        assertThat(result.reservedQuantity()).isEqualByComparingTo("2.500");
    }

    @Test
    void consumeReservation_maiorQueOReservado_lancaIllegalArgument() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);

        assertThatThrownBy(() -> balance.consumeReservation(new BigDecimal("3.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserve_depoisConsumeReservation_voltaAoEstadoOriginalDeDisponivel() {
        StockBalance original = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        StockBalance reserved = original.reserve(new BigDecimal("3.000"));
        StockBalance consumed = reserved.consumeReservation(new BigDecimal("3.000"));

        assertThat(consumed.quantity()).isEqualByComparingTo("7.000");
        assertThat(consumed.reservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reserve_depoisReleaseReservation_voltaAoFisicoOriginal() {
        StockBalance original = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        StockBalance reserved = original.reserve(new BigDecimal("3.000"));
        StockBalance released = reserved.releaseReservation(new BigDecimal("3.000"));

        assertThat(released.quantity()).isEqualByComparingTo("10.000");
        assertThat(released.reservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void apply_entrada_naoMexeNoReservedQuantity() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("2.000"));

        assertThat(result.reservedQuantity()).isEqualByComparingTo("4.000");
    }

    @Test
    void apply_saida_dentroDoDisponivelApesarDeExistirReserva_ehPermitida() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        StockBalance result = balance.apply(MovementType.SAIDA, new BigDecimal("6.000"));

        assertThat(result.quantity()).isEqualByComparingTo("4.000");
        assertThat(result.reservedQuantity()).as("SAIDA comum não consome a reserva")
                .isEqualByComparingTo("4.000");
    }

    /** Físico bastaria (10), mas 4 já estão prometidas a outro pedido: disponível é só 6. */
    @Test
    void apply_saida_fisicoBastaMasReservaImpede_lancaReservedStockException() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        assertThatThrownBy(() -> balance.apply(MovementType.SAIDA, new BigDecimal("7.000")))
                .isInstanceOf(ReservedStockException.class);
    }

    @Test
    void apply_saida_semFisicoSuficiente_continuaLancandoInsufficientStock() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("1.000"), 0L);

        assertThatThrownBy(() -> balance.apply(MovementType.SAIDA, new BigDecimal("6.000")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void apply_ajuste_exatamenteIgualAoReservado_ehPermitido() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("4.000"));

        assertThat(result.quantity()).isEqualByComparingTo("4.000");
        assertThat(result.reservedQuantity()).isEqualByComparingTo("4.000");
    }

    @Test
    void apply_ajuste_abaixoDoReservado_lancaReservedStockException() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("4.000"), 0L);

        assertThatThrownBy(() -> balance.apply(MovementType.AJUSTE, new BigDecimal("3.000")))
                .isInstanceOf(ReservedStockException.class);
    }

    // ── Custo médio ponderado (EST-F007) ────────────────────────────────────────────────────

    @Test
    void apply_entrada_comUnitCost_primeiraEntradaAssumeOCustoInformado() {
        StockBalance balance = StockBalance.zero("NARG-001", 1L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("10"), new BigDecimal("5.00"));

        assertThat(result.averageCost()).isEqualByComparingTo("5.00");
    }

    @Test
    void apply_entrada_comUnitCost_segundaEntradaPonderaPeloSaldo() {
        // 10 un a R$5 + 10 un a R$7 = 20 un a R$6 (exemplo didático do plano).
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("5.00"), 0L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("10"), new BigDecimal("7.00"));

        assertThat(result.quantity()).isEqualByComparingTo("20");
        assertThat(result.averageCost()).isEqualByComparingTo("6.00");
    }

    @Test
    void apply_entrada_semUnitCost_naoAlteraAverageCost() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("5.00"), 0L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("10"));

        assertThat(result.quantity()).isEqualByComparingTo("20");
        assertThat(result.averageCost()).isEqualByComparingTo("5.00");
    }

    @Test
    void apply_entrada_semUnitCostENuncaTeveCusto_permaneceNulo() {
        StockBalance balance = StockBalance.zero("NARG-001", 1L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("10"));

        assertThat(result.averageCost()).isNull();
    }

    @Test
    void apply_saida_propagaAverageCostSemRecalcular() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("6.00"), 0L);

        StockBalance result = balance.apply(MovementType.SAIDA, new BigDecimal("4"));

        assertThat(result.averageCost()).isEqualByComparingTo("6.00");
    }

    @Test
    void apply_saida_ateZero_resetaAverageCostParaNulo() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("6.00"), 0L);

        StockBalance result = balance.apply(MovementType.SAIDA, new BigDecimal("10"));

        assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.averageCost()).isNull();
    }

    @Test
    void apply_ajuste_propagaAverageCostSemRecalcular() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("6.00"), 0L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("3"));

        assertThat(result.averageCost()).isEqualByComparingTo("6.00");
    }

    @Test
    void apply_ajuste_paraZero_resetaAverageCostParaNulo() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("6.00"), 0L);

        StockBalance result = balance.apply(MovementType.AJUSTE, BigDecimal.ZERO);

        assertThat(result.averageCost()).isNull();
    }

    @Test
    void apply_entrada_comUnitCost_arredondaComDizima() {
        // 3 un a R$10.00 + 7 un a R$10.005 = (30 + 70.035) / 10 = 10.0035 → 10.00 (HALF_UP, 2 casas).
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("3"),
                BigDecimal.ZERO, new BigDecimal("10.00"), 0L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("7"), new BigDecimal("10.005"));

        assertThat(result.averageCost()).isEqualByComparingTo("10.00");
    }

    @Test
    void consumeReservation_ateZero_resetaAverageCostParaNulo() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5"),
                new BigDecimal("5"), new BigDecimal("6.00"), 0L);

        StockBalance result = balance.consumeReservation(new BigDecimal("5"));

        assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.averageCost()).isNull();
    }

    @Test
    void consumeReservation_acimaDeZero_propagaAverageCost() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10"),
                new BigDecimal("5"), new BigDecimal("6.00"), 0L);

        StockBalance result = balance.consumeReservation(new BigDecimal("5"));

        assertThat(result.averageCost()).isEqualByComparingTo("6.00");
    }
}
