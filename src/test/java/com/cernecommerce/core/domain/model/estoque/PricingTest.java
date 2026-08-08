package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    class Invariantes {

        @Test
        void emptyNaoTemNenhumCampoDefinido() {
            Pricing empty = Pricing.empty();

            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.costPrice()).isNull();
            assertThat(empty.markupPercent()).isNull();
            assertThat(empty.salePrice()).isNull();
        }

        @Test
        void aceitaOsTresCamposNulos() {
            assertThat(Pricing.of(null, null, null).isEmpty()).isTrue();
        }

        @Test
        void aceitaCustoSemPrecoDeVenda() {
            Pricing pricing = Pricing.of(bd("45.00"), null, null);

            assertThat(pricing.isEmpty()).isFalse();
            assertThat(pricing.isPriced()).isFalse();
        }

        @Test
        void rejeitaCustoNegativo() {
            assertThatThrownBy(() -> Pricing.of(bd("-0.01"), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("costPrice");
        }

        @Test
        void rejeitaMarkupNegativo() {
            assertThatThrownBy(() -> Pricing.of(null, bd("-1"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("markupPercent");
        }

        @Test
        void rejeitaPrecoDeVendaNegativo() {
            assertThatThrownBy(() -> Pricing.of(null, null, bd("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("salePrice");
        }

        @Test
        void aceitaZeroNosTresCampos() {
            Pricing pricing = Pricing.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            assertThat(pricing.isPriced()).isTrue();
            assertThat(pricing.isBelowCost()).isFalse();
        }
    }

    @Nested
    class PrecoSugerido {

        @Test
        void markupDe100PorCentoDobraOCusto() {
            assertThat(Pricing.byMarkup(bd("50.00"), bd("100")).suggestedPrice())
                    .isEqualByComparingTo("100.00");
        }

        @Test
        void markupDeZeroVendeAoCusto() {
            assertThat(Pricing.byMarkup(bd("50.00"), BigDecimal.ZERO).suggestedPrice())
                    .isEqualByComparingTo("50.00");
        }

        @Test
        void markupFracionadoArredondaParaCentavos() {
            // 45,00 × 1,335 = 60,075 → HALF_UP → 60,08
            assertThat(Pricing.byMarkup(bd("45.00"), bd("33.5")).suggestedPrice())
                    .isEqualByComparingTo("60.08");
        }

        @Test
        void markupComQuatroCasasPreservaOInput() {
            // A escala 4 de markup_percent existe para este caso: 33,3333% sobre 45,00
            // reproduz 60,00 em vez de escorregar por causa de arredondamento do input.
            assertThat(Pricing.byMarkup(bd("45.00"), bd("33.3333")).suggestedPrice())
                    .isEqualByComparingTo("60.00");
        }

        @Test
        void semCustoNaoHaSugestao() {
            assertThat(Pricing.of(null, bd("80"), null).suggestedPrice()).isNull();
        }

        @Test
        void semMarkupNaoHaSugestao() {
            assertThat(Pricing.of(bd("45.00"), null, null).suggestedPrice()).isNull();
        }
    }

    @Nested
    class PrecoEfetivo {

        @Test
        void precoPraticadoVenceOSugerido() {
            // Markup de 80% sobre 45,00 sugere 81,00, mas a loja cobra 79,90.
            Pricing pricing = Pricing.of(bd("45.00"), bd("80"), bd("79.90"));

            assertThat(pricing.suggestedPrice()).isEqualByComparingTo("81.00");
            assertThat(pricing.effectivePrice()).isEqualByComparingTo("79.90");
        }

        @Test
        void semPrecoPraticadoCaiNoSugerido() {
            assertThat(Pricing.byMarkup(bd("45.00"), bd("80")).effectivePrice())
                    .isEqualByComparingTo("81.00");
        }

        @Test
        void semPrecoNemSugestaoNaoEstaPrecificado() {
            assertThat(Pricing.empty().effectivePrice()).isNull();
            assertThat(Pricing.empty().isPriced()).isFalse();
        }

        @Test
        void precoPraticadoSemCustoAindaPrecifica() {
            // Revenda sem custo cadastrado ainda vende; o que não dá é calcular margem.
            Pricing pricing = Pricing.of(null, null, bd("79.90"));

            assertThat(pricing.isPriced()).isTrue();
            assertThat(pricing.effectivePrice()).isEqualByComparingTo("79.90");
            assertThat(pricing.marginAmount()).isNull();
        }
    }

    @Nested
    class MargemEMarkupEfetivo {

        @Test
        void custo50EVenda100SaoMarkupDe100EMargemDe50() {
            // O ponto que a documentação de Pricing existe para não deixar esquecer: a mesma
            // diferença de R$ 50 é 100% de markup (sobre o custo) e 50% de margem (sobre a venda).
            Pricing pricing = Pricing.of(bd("50.00"), null, bd("100.00"));

            assertThat(pricing.marginAmount()).isEqualByComparingTo("50.00");
            assertThat(pricing.effectiveMarkupPercent()).isEqualByComparingTo("100.00");
            assertThat(pricing.marginPercent()).isEqualByComparingTo("50.00");
        }

        @Test
        void markupEfetivoRevelaOArredondamentoComercial() {
            // Pretendia 80% de markup; cobrando 79,90 em vez de 81,00, entrega 77,56%.
            Pricing pricing = Pricing.of(bd("45.00"), bd("80"), bd("79.90"));

            assertThat(pricing.markupPercent()).isEqualByComparingTo("80");
            assertThat(pricing.effectiveMarkupPercent()).isEqualByComparingTo("77.56");
        }

        @Test
        void margemUsaOPrecoEfetivoQuandoNaoHaPrecoPraticado() {
            Pricing pricing = Pricing.byMarkup(bd("50.00"), bd("100"));

            assertThat(pricing.marginAmount()).isEqualByComparingTo("50.00");
            assertThat(pricing.marginPercent()).isEqualByComparingTo("50.00");
        }

        @Test
        void margemENulaSemCusto() {
            Pricing pricing = Pricing.of(null, null, bd("79.90"));

            assertThat(pricing.marginPercent()).isNull();
            assertThat(pricing.effectiveMarkupPercent()).isNull();
        }

        @Test
        void margemPercentualENulaQuandoPrecoEfetivoEZero() {
            // Brinde: divisão por zero não é margem de 0%, é margem indefinida.
            Pricing pricing = Pricing.of(bd("10.00"), null, BigDecimal.ZERO);

            assertThat(pricing.marginPercent()).isNull();
            assertThat(pricing.marginAmount()).isEqualByComparingTo("-10.00");
        }

        @Test
        void markupEfetivoENuloQuandoCustoEZero() {
            // Custo zero (brinde de fornecedor): todo markup seria infinito.
            Pricing pricing = Pricing.of(BigDecimal.ZERO, null, bd("20.00"));

            assertThat(pricing.effectiveMarkupPercent()).isNull();
            assertThat(pricing.marginPercent()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    class VendaAbaixoDoCusto {

        @Test
        void sinalizaPrejuizoSemBloquear() {
            Pricing pricing = Pricing.of(bd("50.00"), null, bd("40.00"));

            assertThat(pricing.isBelowCost()).isTrue();
            assertThat(pricing.marginAmount()).isEqualByComparingTo("-10.00");
            assertThat(pricing.marginPercent()).isEqualByComparingTo("-25.00");
        }

        @Test
        void vendaExatamenteAoCustoNaoEPrejuizo() {
            assertThat(Pricing.of(bd("50.00"), null, bd("50.00")).isBelowCost()).isFalse();
        }

        @Test
        void semCustoNaoHaComoSaberSeEPrejuizo() {
            assertThat(Pricing.of(null, null, bd("40.00")).isBelowCost()).isFalse();
        }
    }

    @Nested
    class PatchParcial {

        @Test
        void campoNuloMantemOValorAtual() {
            Pricing atual = Pricing.of(bd("45.00"), bd("80"), bd("79.90"));

            Pricing patched = atual.withPatch(bd("50.00"), null, null);

            assertThat(patched.costPrice()).isEqualByComparingTo("50.00");
            assertThat(patched.markupPercent()).isEqualByComparingTo("80");
            assertThat(patched.salePrice()).isEqualByComparingTo("79.90");
        }

        @Test
        void patchVazioNaoAlteraNada() {
            Pricing atual = Pricing.of(bd("45.00"), bd("80"), bd("79.90"));

            assertThat(atual.withPatch(null, null, null)).isEqualTo(atual);
        }

        @Test
        void patchSobreEmptyPrecificaDoZero() {
            Pricing patched = Pricing.empty().withPatch(bd("45.00"), bd("80"), null);

            assertThat(patched.effectivePrice()).isEqualByComparingTo("81.00");
        }

        @Test
        void trocarOCustoMoveOPrecoSugeridoMasNaoOPraticado() {
            Pricing atual = Pricing.of(bd("45.00"), bd("80"), bd("79.90"));

            Pricing patched = atual.withPatch(bd("60.00"), null, null);

            assertThat(patched.suggestedPrice()).isEqualByComparingTo("108.00");
            assertThat(patched.effectivePrice()).isEqualByComparingTo("79.90");
        }
    }

    @Nested
    class MaterializarSugestao {

        @Test
        void congelaOSugeridoComoPraticado() {
            Pricing materialized = Pricing.byMarkup(bd("45.00"), bd("80")).materializeSuggestion();

            assertThat(materialized.salePrice()).isEqualByComparingTo("81.00");
            assertThat(materialized.effectivePrice()).isEqualByComparingTo("81.00");
        }

        @Test
        void semSugestaoRetornaOProprioObjeto() {
            Pricing semMarkup = Pricing.of(bd("45.00"), null, null);

            assertThat(semMarkup.materializeSuggestion()).isSameAs(semMarkup);
        }

        @Test
        void depoisDeCongelarMexerNoCustoNaoMoveMaisOPreco() {
            Pricing congelado = Pricing.byMarkup(bd("45.00"), bd("80")).materializeSuggestion();

            Pricing custoNovo = congelado.withPatch(bd("60.00"), null, null);

            assertThat(custoNovo.effectivePrice()).isEqualByComparingTo("81.00");
            assertThat(custoNovo.suggestedPrice()).isEqualByComparingTo("108.00");
        }
    }

    @Nested
    class PrecoDePor {

        @Test
        void rejeitaOriginalPriceNegativo() {
            assertThatThrownBy(() -> Pricing.of(null, null, null, bd("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("originalPrice");
        }

        @Test
        void semOriginalPriceNaoHaDesconto() {
            Pricing pricing = Pricing.of(bd("45.00"), null, bd("79.90"));

            assertThat(pricing.hasDiscount()).isFalse();
            assertThat(pricing.discountPercent()).isNull();
        }

        @Test
        void originalPriceMaiorQueOEfetivoTemDesconto() {
            Pricing pricing = Pricing.of(bd("45.00"), null, bd("79.90"), bd("99.90"));

            assertThat(pricing.hasDiscount()).isTrue();
            // (99.90 - 79.90) / 99.90 * 100 = 20.0200...
            assertThat(pricing.discountPercent()).isEqualByComparingTo("20.02");
        }

        @Test
        void originalPriceIgualAoEfetivoNaoTemDesconto() {
            // Selo "de/por" não deve mentir: preço "de" igual ao praticado não é desconto real.
            Pricing pricing = Pricing.of(bd("45.00"), null, bd("79.90"), bd("79.90"));

            assertThat(pricing.hasDiscount()).isFalse();
            assertThat(pricing.discountPercent()).isNull();
        }

        @Test
        void originalPriceMenorQueOEfetivoNaoTemDesconto() {
            Pricing pricing = Pricing.of(bd("45.00"), null, bd("79.90"), bd("50.00"));

            assertThat(pricing.hasDiscount()).isFalse();
            assertThat(pricing.discountPercent()).isNull();
        }

        @Test
        void semPrecoEfetivoNaoHaDesconto() {
            Pricing pricing = Pricing.of(null, null, null, bd("99.90"));

            assertThat(pricing.hasDiscount()).isFalse();
            assertThat(pricing.discountPercent()).isNull();
        }

        @Test
        void patchDe4ArgumentosAlteraOriginalPrice() {
            Pricing atual = Pricing.of(bd("45.00"), bd("80"), bd("79.90"));

            Pricing patched = atual.withPatch(null, null, null, bd("99.90"));

            assertThat(patched.originalPrice()).isEqualByComparingTo("99.90");
            assertThat(patched.costPrice()).isEqualByComparingTo("45.00");
        }

        @Test
        void patchDe3ArgumentosNaoTocaEmOriginalPrice() {
            Pricing atual = Pricing.of(bd("45.00"), bd("80"), bd("79.90"), bd("99.90"));

            Pricing patched = atual.withPatch(bd("50.00"), null, null);

            assertThat(patched.originalPrice()).isEqualByComparingTo("99.90");
            assertThat(patched.costPrice()).isEqualByComparingTo("50.00");
        }

        @Test
        void materializeSuggestionPreservaOriginalPrice() {
            Pricing pricing = Pricing.of(bd("45.00"), bd("80"), null, bd("99.90"));

            Pricing materialized = pricing.materializeSuggestion();

            assertThat(materialized.originalPrice()).isEqualByComparingTo("99.90");
            assertThat(materialized.salePrice()).isEqualByComparingTo("81.00");
        }

        @Test
        void ofDeTresArgumentosDeixaOriginalPriceNulo() {
            assertThat(Pricing.of(bd("45.00"), bd("80"), bd("79.90")).originalPrice()).isNull();
        }
    }
}
