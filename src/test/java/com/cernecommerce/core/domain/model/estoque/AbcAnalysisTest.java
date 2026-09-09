package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.model.estoque.AbcAnalysis.AbcEntry;
import com.cernecommerce.core.domain.model.estoque.AbcAnalysis.ConsumptionLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EST-F011 — a aritmética da curva ABC, exercitada sem banco.
 *
 * <p>É o motivo de a classificação morar no domínio e não em SQL: percentual acumulado, empate e
 * saldo zero são onde este tipo de relatório erra, e nenhum deles precisa de Postgres para ser
 * provado.</p>
 */
class AbcAnalysisTest {

    private static ConsumptionLine line(String sku, String qty, String value, String balance) {
        return new ConsumptionLine(sku, "Produto " + sku, new BigDecimal(qty), new BigDecimal(value),
                balance == null ? null : new BigDecimal(balance));
    }

    @Test
    void classify_ordersByValueDescending() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("BARATO", "100", "10.00", "50"),
                line("CARO", "2", "900.00", "10"),
                line("MEDIO", "20", "90.00", "30")));

        assertThat(result).extracting(AbcEntry::sku).containsExactly("CARO", "MEDIO", "BARATO");
    }

    /**
     * O ponto do relatório: quem sai MAIS não é quem pesa mais. BARATO teve 100 unidades de saída
     * contra 2 de CARO, e ainda assim é C — a curva classifica dinheiro, não movimento.
     */
    @Test
    void classify_putsTheExpensiveLowVolumeSkuInA_andTheCheapHighVolumeInC() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("BARATO", "100", "10.00", "50"),
                line("CARO", "2", "900.00", "10"),
                line("MEDIO", "20", "90.00", "30")));

        // CARO sozinho vale 90% do consumo. Pelo acumulado de DEPOIS ele sairia B — o item mais
        // caro da loja fora da faixa de atenção, que é o oposto do que a curva deve dizer.
        assertThat(result.get(0).abcClass()).isEqualTo(AbcClass.A);
        assertThat(result.get(0).sku()).isEqualTo("CARO");
        assertThat(result.get(0).cumulativePercent()).isEqualByComparingTo("90.00");
        assertThat(result.get(2).abcClass()).isEqualTo(AbcClass.C);
        assertThat(result.get(2).sku()).isEqualTo("BARATO");
    }

    /** O acumulado da última linha fecha em 100% — se não fechar, a base do rateio está errada. */
    @Test
    void classify_accumulatesToOneHundredPercent() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("A", "1", "500.00", "10"),
                line("B", "1", "300.00", "10"),
                line("C", "1", "200.00", "10")));

        assertThat(result.get(result.size() - 1).cumulativePercent()).isEqualByComparingTo("100.00");
        assertThat(result).extracting(AbcEntry::cumulativePercent)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("80.00"), new BigDecimal("100.00"));
    }

    /**
     * A linha que <b>cruza</b> os 80% ainda é A: o acumulado antes dela era 50%, então ela estava
     * dentro da faixa quando entrou. É o que impede o item que atravessa o limiar de ser jogado para
     * baixo por acidente de arredondamento.
     */
    @Test
    void classify_keepsTheLineThatCrossesTheThresholdInTheFaixaItWasCrossing() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("A", "1", "500.00", "10"),
                line("B", "1", "300.00", "10"),
                line("C", "1", "200.00", "10")));

        assertThat(result.get(0).abcClass()).isEqualTo(AbcClass.A);
        // Acumulado antes = 50% → ainda A, embora feche exatamente em 80%.
        assertThat(result.get(1).cumulativePercent()).isEqualByComparingTo("80.00");
        assertThat(result.get(1).abcClass()).isEqualTo(AbcClass.A);
        // Acumulado antes = 80% → sai de A, entra em B.
        assertThat(result.get(2).abcClass()).isEqualTo(AbcClass.B);
    }

    /** Toda linha recebe exatamente uma classe — nenhuma fica sem. */
    @Test
    void classify_assignsExactlyOneClassToEveryLine() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("A", "1", "800.00", "10"),
                line("B", "1", "140.00", "10"),
                line("C", "1", "60.00", "10")));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(AbcEntry::abcClass).doesNotContainNull();
        assertThat(result).extracting(AbcEntry::abcClass)
                .containsExactly(AbcClass.A, AbcClass.B, AbcClass.B);
    }

    /**
     * Empate de valor desempata por SKU. Sem isso, duas chamadas com os mesmos dados poderiam
     * devolver ordens diferentes e o relatório pareceria mudar sozinho.
     */
    @Test
    void classify_breaksValueTiesBySkuForAStableOrder() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("ZZZ", "1", "100.00", "10"),
                line("AAA", "1", "100.00", "10")));

        assertThat(result).extracting(AbcEntry::sku).containsExactly("AAA", "ZZZ");
    }

    /** Período sem saída nenhuma devolve lista vazia, não erro. */
    @Test
    void classify_withNoConsumptionReturnsEmpty() {
        assertThat(AbcAnalysis.classify(List.of())).isEmpty();
        assertThat(AbcAnalysis.classify(null)).isEmpty();
    }

    /** SKU sem custo médio vale zero e continua no relatório, em C — não some. */
    @Test
    void classify_keepsSkusWithoutAverageCostInC() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("COM-CUSTO", "1", "1000.00", "10"),
                line("SEM-CUSTO", "50", "0.00", "10")));

        assertThat(result).extracting(AbcEntry::sku).contains("SEM-CUSTO");
        assertThat(result.get(0).abcClass()).isEqualTo(AbcClass.A);
        assertThat(result.get(1).abcClass()).isEqualTo(AbcClass.C);
        assertThat(result.get(1).consumedQuantity()).isEqualByComparingTo("50");
    }

    /** Todo o consumo valendo zero não pode dividir por zero. */
    @Test
    void classify_withZeroTotalValueDoesNotDivideByZero() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(
                line("X", "5", "0.00", "10"),
                line("Y", "3", "0.00", "10")));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(e -> assertThat(e.abcClass()).isEqualTo(AbcClass.C));
    }

    @Test
    void turnover_isConsumptionOverCurrentBalance() {
        List<AbcEntry> result = AbcAnalysis.classify(List.of(line("X", "20", "100.00", "8")));

        assertThat(result.get(0).turnover()).isEqualByComparingTo("2.50");
    }

    /**
     * Saldo zero devolve giro <b>nulo</b>, não um número enorme: um SKU em ruptura não é o produto
     * que mais gira da loja, é o que perdeu o denominador.
     */
    @Test
    void turnover_isNullWhenTheBalanceIsZero() {
        List<AbcEntry> zerado = AbcAnalysis.classify(List.of(line("X", "20", "100.00", "0")));
        List<AbcEntry> semSaldo = AbcAnalysis.classify(List.of(line("Y", "20", "100.00", null)));

        assertThat(zerado.get(0).turnover()).isNull();
        assertThat(semSaldo.get(0).turnover()).isNull();
    }

    @Test
    void consumptionLine_rejectsBlankSkuAndNegativeConsumption() {
        assertThatThrownBy(() -> line(" ", "1", "1.00", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("X", "-1", "1.00", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
