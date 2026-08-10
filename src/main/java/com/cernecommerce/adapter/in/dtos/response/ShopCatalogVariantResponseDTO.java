package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ShopCatalogVariantResponseDTO {
    private String sku;
    private List<ProductAttributeResponseDTO> attributes;
    private boolean available;

    /**
     * Preço efetivo desta variação (EST-F020): o próprio, quando cadastrado, ou o herdado do
     * produto pai. Já resolvido pelo backend — a vitrine não reimplementa a precedência.
     */
    private BigDecimal price;
}
