package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Tipo de utensílio da sessão (PDV-F021). O código não muda depois de criado. */
@Data
public class SessionAssetTypeRequest {

    @Size(max = 30)
    @Schema(description = "Só no POST. Maiúsculas, sem espaço.", example = "PINCA")
    private String codigo;

    @NotBlank
    @Size(max = 60)
    @Schema(example = "Pinça")
    private String nome;

    @Min(0)
    @Schema(description = "Quantas a casa tem.", example = "12")
    private int quantidadeTotal;

    @Schema(description = "Acompanha toda sessão (pinça, prato, tapete). Vaso não é incluso: vem da configuração.")
    private boolean incluso;

    @Schema(description = "Só no PUT.", defaultValue = "true")
    private Boolean ativo;
}
