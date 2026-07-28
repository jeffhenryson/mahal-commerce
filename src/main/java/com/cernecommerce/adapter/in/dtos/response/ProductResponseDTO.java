package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class ProductResponseDTO {
    private Long id;
    private String sku;
    private String name;
    private String category;
    private boolean active;
    private List<ProductVariantResponseDTO> variants;

    /** EST-F019 — nunca nulo; produto sem preço vem com os campos internos nulos. */
    private PricingResponseDTO pricing;
}
