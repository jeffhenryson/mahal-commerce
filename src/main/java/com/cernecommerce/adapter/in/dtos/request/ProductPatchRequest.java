package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alteração parcial de produto (EST-F018). Campo ausente ou nulo significa <b>não mexer</b>;
 * por isso nenhum é {@code @NotBlank} — mas se vier, precisa ser válido.
 *
 * <p>{@code sku} e {@code variants} não estão aqui de propósito: o SKU é a identidade
 * referenciada como texto livre pelas tabelas de estoque, e mexer na grade de variações precisa
 * da validação de duplicidade de {@code POST /estoque/products}.</p>
 *
 * <p>{@code onSale} é {@code Boolean} (wrapper), não {@code boolean} — é um toggle puro (mesma
 * natureza de {@code active}/{@code lotTracked}, que têm PATCH dedicado), sem valor "vazio"
 * natural para representar "não mexer" como o {@code null} de String faz para os demais campos
 * deste DTO. Aqui nulo mantém, e {@code true}/{@code false} explícitos trocam.</p>
 */
@Data
public class ProductPatchRequest {

    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 100)
    private String category;

    @Size(max = 100)
    private String brand;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente. Nulo mantém a atual. */
    @Size(max = 2048)
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção. Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean onSale;

    /**
     * Precificação (EST-F019). Ausente, mantém a atual; presente, cada um dos seus campos segue
     * a mesma regra de "nulo mantém". Exige {@code ESTOQUE_PRODUCT_PRICE_MANAGE} — mandar este
     * bloco sem a permissão é 403, e nada do PATCH é aplicado.
     */
    @Valid
    private PricingRequest pricing;
}
