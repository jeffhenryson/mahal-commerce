package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Resumo pré-calculado do catálogo de estoque (EST-F022). Evita que cada tela/widget que precisa
 * só desses números baixe e cruze o catálogo inteiro em memória a cada carregamento.
 */
@Data
@Schema(description = "Números agregados do catálogo de estoque")
public class EstoqueSummaryResponseDTO {

    @Schema(description = "Quantidade de SKUs pai no catálogo, ativos ou não", example = "342")
    private long totalProdutos;

    @Schema(description = "Quantidade de variações (SKU filho) em todo o catálogo", example = "891")
    private long totalVariantes;

    @Schema(description = "Soma de quantidade × custo efetivo de todo saldo em todos os depósitos. "
            + "Saldo sem custo conhecido contribui zero.", example = "128450.30")
    private BigDecimal valorEstoqueCusto;

    @Schema(description = "Pares SKU/depósito em alerta crítico de reposição", example = "7")
    private long alertasCriticos;

    @Schema(description = "Pares SKU/depósito em alerta de atenção de reposição", example = "15")
    private long alertasAtencao;

    @Schema(description = "Categoria com mais produtos vinculados, ou nulo se nenhum produto está vinculado a uma categoria")
    private CategoriaComMaisProdutosDTO categoriaComMaisProdutos;

    @Data
    @Schema(description = "Categoria com mais produtos vinculados")
    public static class CategoriaComMaisProdutosDTO {
        @Schema(example = "Bebidas")
        private String nome;

        @Schema(example = "58")
        private long quantidade;
    }
}
