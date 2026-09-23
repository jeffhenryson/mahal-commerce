package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Um produto (ou variação) escolhível num passo do kit montável. {@code sku} é o que vai na escolha. */
@Data
public class KitStepOptionResponseDTO {
    private String sku;
    private String productSku;
    private String name;
    private List<ProductAttribute> attributes;
    private BigDecimal price;
    private String imageUrl;
    private Boolean available;
}
