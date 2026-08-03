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

    /** {@code SIMPLES} ou {@code KIT} (EST-F015). */
    private String type;

    /** EST-F008 — opt-in: ENTRADA deste SKU passa a exigir lote e validade. */
    private boolean lotTracked;
}
