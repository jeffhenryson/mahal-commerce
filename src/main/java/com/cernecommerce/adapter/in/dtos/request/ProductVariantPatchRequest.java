package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Alteração parcial de uma variação já existente (EST-F024). Campo ausente ou nulo é mantido —
 * mesma semântica de {@code ProductPatchRequest}.
 *
 * <p>{@code sku} não entra: renomear o SKU de uma variação transformaria em órfão todo o
 * histórico de estoque que a referencia como texto livre ({@code stock_balance},
 * {@code stock_movement}, {@code stock_reorder_point}) — mesmo motivo de {@code sku} não entrar
 * em {@code ProductPatchRequest}.</p>
 */
@Data
public class ProductVariantPatchRequest {

    /** Ativa/desativa a variação. Nulo mantém; {@code true}/{@code false} explícitos trocam. */
    private Boolean active;

    /**
     * Atributos da variação. Nulo mantém os atuais; uma lista (mesmo vazia) substitui o conjunto
     * inteiro — mesma semântica de {@code ProductPatchRequest.attributes}.
     */
    @Valid
    @Size(max = 20)
    private List<ProductAttributeRequest> attributes;

    /**
     * Precificação própria da variação (EST-F020). Ausente, mantém a atual; presente, cada campo
     * segue a mesma regra de "nulo mantém". Exige {@code ESTOQUE_PRODUCT_PRICE_MANAGE}.
     */
    @Valid
    private PricingRequest pricing;

    /** Código de barras/EAN próprio da variação. Nulo mantém o atual. */
    @Pattern(regexp = "^[0-9]{8,14}$", message = "barcode deve ter entre 8 e 14 dígitos numéricos")
    private String barcode;

    /** Contraparte de {@code ProductPatchRequest.touchesPricing()}, usada pelo mesmo {@code @PreAuthorize}. */
    public boolean touchesPricing() {
        return pricing != null;
    }
}
