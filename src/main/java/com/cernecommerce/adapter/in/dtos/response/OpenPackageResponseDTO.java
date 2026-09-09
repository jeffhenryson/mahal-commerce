package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/** Lata aberta rendendo sessões (EST-F027). */
@Data
@Schema(description = "Pacote em uso — a lata de essência já aberta no balcão")
public class OpenPackageResponseDTO {

    @Schema(description = "SKU da essência", example = "ESSE-ZGY-BLUEBERRY")
    private String sku;

    @Schema(description = "Nome do produto, para a tela não cruzar com o catálogo por linha",
            example = "Zgy Blueberry")
    private String productName;

    @Schema(description = "Depósito onde a lata está aberta", example = "LOJA-01")
    private String warehouseCode;

    @Schema(description = "Sessões já lançadas nesta lata", example = "3")
    private int uses;

    @Schema(description = "Quantas sessões esta lata rende — cópia do cadastro no momento da "
            + "abertura, não leitura viva: editar o produto não muda o tamanho de uma lata pela "
            + "metade.", example = "5")
    private int sessionsPerUnit;

    @Schema(description = "Sessões que ainda cabem nesta lata", example = "2")
    private int remaining;

    @Schema(description = "A lata rendeu tudo o que tinha. Continua aberta neste estado — é a que "
            + "o atendente está terminando —, e a próxima sessão abre outra.", example = "false")
    private boolean exhausted;

    @Schema(description = "Quando a lata foi aberta")
    private Instant openedAt;

    @Schema(description = "Quem abriu", example = "atendente")
    private String openedBy;
}
