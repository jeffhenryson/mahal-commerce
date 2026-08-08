package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String category;

    @Size(max = 100)
    private String brand;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente, não upload de arquivo. */
    @Size(max = 2048)
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção. Omitido, nasce {@code false}. */
    private boolean onSale;

    /** Selo de destaque distinto de {@code onSale}. Omitido, nasce {@code false}. */
    private boolean superPromo;

    /** Descrição longa do produto, opcional. Sem limite curto como {@code name}. */
    @Size(max = 5000)
    private String description;

    /** Estágio 01 do admin — link de vídeo cadastrado manualmente, mesma convenção de {@code imageUrl}. */
    @Size(max = 2048)
    private String videoUrl;

    /** Galeria de até 5 imagens ordenadas, opcional. */
    @Size(max = 5)
    private List<@Size(max = 2048) String> images;

    @Valid
    private List<ProductVariantRequest> variants;

    /** Opcional (EST-F019) — omitido, o produto nasce sem precificação. */
    @Valid
    private PricingRequest pricing;
}
