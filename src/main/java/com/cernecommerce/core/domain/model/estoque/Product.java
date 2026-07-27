package com.cernecommerce.core.domain.model.estoque;

import java.util.List;

/**
 * Produto (SKU pai) da grade de estoque. Agrega as variações (SKU filhos), cada
 * uma distinguida por seus {@link ProductAttribute} (sabor, tamanho, cor).
 */
public record Product(
    Long id,
    String sku,
    String name,
    String category,
    boolean active,
    List<ProductVariant> variants
) {

    public Product {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    /** Cria um novo produto (sem id, ativo por padrão). */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants) {
        return new Product(null, sku, name, category, true, variants);
    }

    /** Reconstitui um produto a partir de persistência. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants) {
        return new Product(id, sku, name, category, active, variants);
    }

    /**
     * Alteração parcial (EST-F018): argumento nulo significa <b>não mexer neste campo</b>.
     *
     * <p>Consequência conhecida: como {@code null} quer dizer "manter", não há como <b>limpar</b>
     * a {@code category} por este caminho — o máximo é trocá-la. É o custo da semântica de PATCH
     * sem um wrapper de três estados (ausente / nulo / valor).</p>
     *
     * <p>{@code sku} não entra: é a identidade do produto e aparece como texto livre em
     * {@code stock_balance}, {@code stock_movement} e {@code stock_reorder_point}, sem FK.
     * Renomear o SKU aqui transformaria em órfão todo o histórico do produto (EST-C011).
     * {@code variants} também não entra — mexer na grade altera o espaço de nomes de SKU e
     * precisa da mesma validação de duplicidade de {@code createProduct}.</p>
     */
    public Product withDetails(String newName, String newCategory) {
        return new Product(id, sku,
                newName == null ? name : newName,
                newCategory == null ? category : newCategory,
                active, variants);
    }

    /** Ativa ou desativa o produto, preservando o resto. */
    public Product withActive(boolean newActive) {
        return new Product(id, sku, name, category, newActive, variants);
    }
}
