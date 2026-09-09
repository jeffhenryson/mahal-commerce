package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Classificação ABC e giro de estoque a partir do consumo de um período (EST-F011).
 *
 * <p><b>Função pura, sem estado</b>, no molde de {@code DiscountProration}: recebe as linhas já
 * agregadas pelo repositório e devolve a lista classificada. A aritmética mora aqui, e não em SQL
 * nem no service, porque é a parte que precisa ser exercitada com números na mão — percentual
 * acumulado, empate e lista vazia são onde este tipo de relatório erra, e nenhum deles exige
 * banco para ser provado.</p>
 *
 * <p><b>Por que o valor, e não a quantidade.</b> Curva ABC de estoque classifica por dinheiro
 * imobilizado, não por unidades movimentadas: quem compra precisa saber onde o capital está, e a
 * essência cara que sai pouco pesa mais no caixa do que o carvão barato que sai sempre.</p>
 */
public final class AbcAnalysis {

    /** Corte da faixa A: 80% do valor acumulado. */
    private static final BigDecimal A_THRESHOLD = new BigDecimal("80");

    /** Corte da faixa B: 95% acumulado — os 15% seguintes ao corte de A. */
    private static final BigDecimal B_THRESHOLD = new BigDecimal("95");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private AbcAnalysis() {
    }

    /**
     * Classifica as linhas de consumo, da maior participação para a menor.
     *
     * @param lines consumo agregado por SKU no período, em qualquer ordem
     * @return a lista ordenada por valor decrescente, com percentual acumulado e classe
     */
    public static List<AbcEntry> classify(List<ConsumptionLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        BigDecimal totalValue = lines.stream()
                .map(ConsumptionLine::consumedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ConsumptionLine> sorted = new ArrayList<>(lines);
        // Desempate por SKU: sem ele, dois SKUs de mesmo valor podem trocar de posição entre duas
        // chamadas e um relatório reordenar sozinho sem nada ter mudado.
        sorted.sort(Comparator.comparing(ConsumptionLine::consumedValue).reversed()
                .thenComparing(ConsumptionLine::sku));

        List<AbcEntry> result = new ArrayList<>(sorted.size());
        BigDecimal accumulated = BigDecimal.ZERO;
        for (ConsumptionLine line : sorted) {
            // A classe olha o acumulado ANTES desta linha — ver classOf. O percentual publicado é o
            // de DEPOIS, que é o que se lê numa curva.
            BigDecimal percentBefore = percentOf(accumulated, totalValue);
            accumulated = accumulated.add(line.consumedValue());
            BigDecimal cumulativePercent = percentOf(accumulated, totalValue);
            result.add(new AbcEntry(line.sku(), line.productName(), line.consumedQuantity(),
                    line.consumedValue(), cumulativePercent, classOf(percentBefore, totalValue),
                    turnover(line.consumedQuantity(), line.currentBalance())));
        }
        return List.copyOf(result);
    }

    /**
     * A classe é decidida pelo acumulado <b>antes</b> da linha, e não pelo de depois.
     *
     * <p>A diferença aparece no caso que mais importa: um SKU que sozinho vale 90% do consumo. Pelo
     * acumulado de depois ele sairia <b>B</b> — o item mais caro da loja fora da faixa de atenção,
     * que é o oposto do que a curva existe para dizer. Olhando o acumulado anterior, o item que
     * <i>cruza</i> o limiar pertence à faixa que ele estava cruzando, então o primeiro SKU é sempre
     * A e a leitura fica correta.</p>
     *
     * <p>Consumo total zero joga tudo em C: não há dinheiro a priorizar, e é a leitura honesta de um
     * período sem saída valorizada.</p>
     */
    private static AbcClass classOf(BigDecimal percentBefore, BigDecimal totalValue) {
        if (totalValue.signum() == 0) {
            return AbcClass.C;
        }
        if (percentBefore.compareTo(A_THRESHOLD) < 0) {
            return AbcClass.A;
        }
        return percentBefore.compareTo(B_THRESHOLD) < 0 ? AbcClass.B : AbcClass.C;
    }

    /** Consumo total zero devolve 100% em vez de dividir por zero. */
    private static BigDecimal percentOf(BigDecimal accumulated, BigDecimal totalValue) {
        return totalValue.signum() == 0
                ? HUNDRED
                : accumulated.multiply(HUNDRED).divide(totalValue, Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Giro: consumo do período dividido pelo saldo atual.
     *
     * <p>Saldo zero devolve {@code null}, não infinito: um SKU que acabou não tem giro
     * <i>infinito</i>, ele tem giro <b>desconhecido</b> — a conta perdeu o denominador. Devolver um
     * número enorme faria a ruptura parecer o melhor produto da loja, invertendo exatamente a
     * leitura que o relatório existe para dar.</p>
     */
    private static BigDecimal turnover(BigDecimal consumedQuantity, BigDecimal currentBalance) {
        if (currentBalance == null || currentBalance.signum() <= 0) {
            return null;
        }
        return consumedQuantity.divide(currentBalance, Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Uma linha de consumo agregada pelo repositório, antes da classificação.
     *
     * @param consumedValue quantidade × custo médio. SKU sem custo médio conhecido entra com zero e
     *        cai naturalmente em C, em vez de sumir do relatório — mesma escolha de
     *        {@code CashbackService.findMarginImpact}
     * @param currentBalance saldo atual, denominador do giro
     */
    public record ConsumptionLine(String sku, String productName, BigDecimal consumedQuantity,
            BigDecimal consumedValue, BigDecimal currentBalance) {

        public ConsumptionLine {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku é obrigatório");
            }
            consumedQuantity = consumedQuantity == null ? BigDecimal.ZERO : consumedQuantity;
            consumedValue = consumedValue == null ? BigDecimal.ZERO : consumedValue;
            if (consumedQuantity.signum() < 0 || consumedValue.signum() < 0) {
                throw new IllegalArgumentException("consumo não pode ser negativo: " + sku);
            }
        }
    }

    /** Uma linha já classificada. {@code turnover} é nulo quando o saldo atual é zero. */
    public record AbcEntry(String sku, String productName, BigDecimal consumedQuantity,
            BigDecimal consumedValue, BigDecimal cumulativePercent, AbcClass abcClass,
            BigDecimal turnover) {
    }
}
