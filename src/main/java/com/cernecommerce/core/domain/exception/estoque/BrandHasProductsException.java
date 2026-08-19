package com.cernecommerce.core.domain.exception.estoque;

/**
 * Marca não pode ser removida enquanto houver produto vinculado — {@code fk_product_brand} não
 * tem {@code ON DELETE CASCADE}/{@code SET NULL}, então apagar deixaria {@code product.brand_id}
 * órfão contra a constraint. Mesma régua de {@link CategoryHasProductsException}.
 */
public class BrandHasProductsException extends RuntimeException {
    public BrandHasProductsException(Long brandId, long productCount) {
        super("Marca " + brandId + " não pode ser removida: " + productCount + " produto(s) vinculado(s)");
    }
}
