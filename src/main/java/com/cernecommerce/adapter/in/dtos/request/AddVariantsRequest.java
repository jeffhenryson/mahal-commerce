package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Uma ou mais variações novas a acrescentar à grade de um produto já existente (EST-F024).
 * Aditivo por natureza — não substitui nem remove nenhuma variação já cadastrada.
 */
@Data
public class AddVariantsRequest {

    @NotEmpty
    @Valid
    private List<ProductVariantRequest> variants;

    /**
     * Mesmo raciocínio de {@code ProductRequest.touchesPricing()}: alguma das variações novas
     * pode vir com {@code pricing} próprio, e isso precisa de {@code ESTOQUE_PRODUCT_PRICE_MANAGE}
     * além de {@code ESTOQUE_PRODUCT_MANAGE}.
     */
    public boolean touchesPricing() {
        return variants != null && variants.stream().anyMatch(v -> v != null && v.getPricing() != null);
    }
}
