package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Item resumido do catálogo público (ECM-F002). Deliberadamente sem custo/markup/margem — esses
 * campos moram em {@link PricingResponseDTO}, exclusiva do admin ({@code ProductResponseDTO}).
 */
@Data
public class ShopCatalogItemResponseDTO {
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    private boolean available;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente pelo lojista. */
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção, usado pela vitrine "Promoções". */
    private boolean onSale;
}
