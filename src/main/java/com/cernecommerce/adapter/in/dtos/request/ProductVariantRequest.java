package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductVariantRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @Valid
    private List<ProductAttributeRequest> attributes;

    /**
     * Precificação própria da variação (EST-F020), opcional. Omitida, a variação <b>herda</b> o
     * preço do produto pai — que é o comportamento padrão e o desejado na maioria dos cadastros
     * (sabores da mesma essência custam o mesmo). Enviá-la exige
     * {@code ESTOQUE_PRODUCT_PRICE_MANAGE}, igual ao bloco {@code pricing} da raiz.
     */
    @Valid
    private PricingRequest pricing;

    /** Código de barras/EAN próprio da variação, opcional. Único quando informado. */
    @Pattern(regexp = "^[0-9]{8,14}$", message = "barcode deve ter entre 8 e 14 dígitos numéricos")
    private String barcode;
}
