package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Lançamento de sessão do cardápio na mesa (PDV-F021). O preço é sempre resolvido pelo servidor. */
@Data
public class AddSessionRequest {

    @NotNull
    @Schema(example = "2")
    private Long tierId;

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Marca e sabor da essência, texto livre.", example = "Zomo Blueberry")
    private String essencia;

    @Schema(description = "Upgrade para o vaso grande (acréscimo da configuração).")
    private boolean vasoGrande;
}
