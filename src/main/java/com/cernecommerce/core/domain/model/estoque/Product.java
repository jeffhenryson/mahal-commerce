package com.cernecommerce.core.domain.model.estoque;

import java.util.List;

/**
 * Produto (SKU pai) da grade de estoque. Agrega as variações (SKU filhos), cada
 * uma distinguida por seus {@link ProductAttribute} (sabor, tamanho, cor), e a
 * {@link Pricing} do SKU pai.
 *
 * <p><b>Preço mora no SKU pai</b> (EST-F019). As variações herdam — sabores diferentes da mesma
 * essência 50g custam o mesmo. Preço por variação (ex.: tamanhos distintos do mesmo narguilé)
 * é EST-F020 no backlog do módulo; até lá, grade com preços diferentes se modela como produtos
 * separados.</p>
 */
public record Product(
    Long id,
    String sku,
    String name,
    String category,
    boolean active,
    List<ProductVariant> variants,
    Pricing pricing,
    ProductType type
) {

    public Product {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        variants = variants == null ? List.of() : List.copyOf(variants);
        pricing = pricing == null ? Pricing.empty() : pricing;
        type = type == null ? ProductType.SIMPLES : type;
    }

    /** Cria um novo produto sem precificação (sem id, ativo por padrão, {@code SIMPLES}). */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants) {
        return create(sku, name, category, variants, Pricing.empty());
    }

    /** Cria um novo produto precificado (sem id, ativo por padrão, {@code SIMPLES}). */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing) {
        return create(sku, name, category, variants, pricing, ProductType.SIMPLES);
    }

    /** Cria um novo produto (sem id, ativo por padrão) — forma canônica, com tipo explícito. */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type) {
        return new Product(null, sku, name, category, true, variants, pricing, type);
    }

    /** Reconstitui um produto sem precificação a partir de persistência. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants) {
        return of(id, sku, name, category, active, variants, Pricing.empty());
    }

    /** Reconstitui um produto a partir de persistência. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing) {
        return of(id, sku, name, category, active, variants, pricing, ProductType.SIMPLES);
    }

    /** Reconstitui um produto a partir de persistência — forma canônica, com tipo explícito. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type) {
        return new Product(id, sku, name, category, active, variants, pricing, type);
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
                active, variants, pricing, type);
    }

    /** Ativa ou desativa o produto, preservando o resto. */
    public Product withActive(boolean newActive) {
        return new Product(id, sku, name, category, newActive, variants, pricing, type);
    }

    /** Substitui a precificação do produto, preservando o resto. */
    public Product withPricing(Pricing newPricing) {
        return new Product(id, sku, name, category, active, variants, newPricing, type);
    }

    /**
     * Promove/rebaixa o tipo do produto, preservando o resto. Usado só por
     * {@code EstoqueService.defineKitRecipe} (EST-F015) — não há endpoint que troque o tipo
     * isoladamente, porque virar {@code KIT} sem uma receita não faz sentido.
     */
    public Product withType(ProductType newType) {
        return new Product(id, sku, name, category, active, variants, pricing, newType);
    }

    /** Indica se este produto é um kit virtual (EST-F015) — sem saldo próprio, saldo derivado. */
    public boolean isKit() {
        return type == ProductType.KIT;
    }
}
