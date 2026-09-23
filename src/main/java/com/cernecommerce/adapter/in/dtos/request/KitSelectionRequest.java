package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * A escolha do cliente no montador de kit (EST-F031): um SKU por escolha, dentro de um passo.
 * Mesmo corpo para cotar, pôr no carrinho (ECM-F008) e lançar na comanda (PDV-F019). Lista vazia
 * passa aqui e é recusada pela regra do kit, com código próprio.
 */
@Data
public class KitSelectionRequest {

    @NotNull
    private Long templateId;

    @NotNull
    @Size(max = 50)
    @Valid
    private List<Pick> picks;

    @Data
    public static class Pick {
        @NotNull
        private Long stepId;

        @NotBlank
        @Size(max = 50)
        private String sku;
    }
}
