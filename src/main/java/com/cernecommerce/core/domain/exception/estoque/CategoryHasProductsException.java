package com.cernecommerce.core.domain.exception.estoque;

/**
 * Categoria não pode ser removida enquanto houver produto vinculado (Bloco 2.3 do BACKEND_TODO de
 * mahal-admin) — {@code fk_product_category} não tem {@code ON DELETE CASCADE}/{@code SET NULL},
 * então apagar deixaria {@code product.category_id} órfão contra a constraint.
 */
public class CategoryHasProductsException extends RuntimeException {
    public CategoryHasProductsException(Long categoryId, long productCount) {
        super("Categoria " + categoryId + " não pode ser removida: " + productCount + " produto(s) vinculado(s)");
    }
}
