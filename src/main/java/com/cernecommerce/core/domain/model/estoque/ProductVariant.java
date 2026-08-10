package com.cernecommerce.core.domain.model.estoque;

import java.util.List;

/**
 * Variação (SKU filho) de um {@link Product}, distinguida por seus {@link ProductAttribute}
 * (ex: sabor, tamanho, cor).
 *
 * <p><b>Preço próprio é opcional</b> (EST-F020). {@code pricing} nulo significa <i>herda do
 * pai</i>, que é o comportamento histórico e segue sendo o padrão: para a tabacaria, sabores
 * diferentes da mesma essência custam o mesmo, e é isso que se quer na esmagadora maioria dos
 * cadastros. Preenchido, sobrescreve — é o caso do vidro de 100g custar mais que o de 50g dentro
 * da mesma grade.</p>
 *
 * <p>Nulo e não {@link Pricing#empty()} de propósito: é preciso distinguir "esta variação não tem
 * preço próprio" de "tem preço próprio, mas ele é desconhecido". Um {@code Pricing} de campos
 * nulos significaria a segunda coisa e apagaria a herança.</p>
 */
public record ProductVariant(Long id, String sku, List<ProductAttribute> attributes, boolean active,
                             Pricing pricing) {

    public ProductVariant {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku da variação é obrigatório");
        }
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /** Cria uma nova variação (sem id, ativa por padrão) que herda o preço do pai. */
    public static ProductVariant create(String sku, List<ProductAttribute> attributes) {
        return create(sku, attributes, null);
    }

    /** Cria uma nova variação (sem id, ativa por padrão). {@code pricing} nulo herda do pai. */
    public static ProductVariant create(String sku, List<ProductAttribute> attributes, Pricing pricing) {
        return new ProductVariant(null, sku, attributes, true, pricing);
    }

    /** Reconstitui uma variação sem preço próprio a partir de persistência. */
    public static ProductVariant of(Long id, String sku, List<ProductAttribute> attributes, boolean active) {
        return of(id, sku, attributes, active, null);
    }

    /** Reconstitui uma variação a partir de persistência. */
    public static ProductVariant of(Long id, String sku, List<ProductAttribute> attributes, boolean active,
            Pricing pricing) {
        return new ProductVariant(id, sku, attributes, active, pricing);
    }

    /**
     * Indica se esta variação declarou <b>algum</b> campo de preço próprio.
     *
     * <p>Repare que a pergunta é essa, e não {@code pricing.isPriced()}: uma variação que só
     * define o próprio custo não tem preço de venda próprio, mas ainda assim sobrescreve o custo
     * do pai — e é esse custo que precisa entrar na soma de um kit. Usar {@code isPriced()} aqui
     * faria o custo próprio da variação ser silenciosamente ignorado.</p>
     *
     * <p>Um {@code Pricing} presente mas totalmente vazio conta como ausência: não há o que
     * sobrescrever.</p>
     */
    public boolean hasOwnPricing() {
        return pricing != null && !pricing.isEmpty();
    }

    /** Substitui a precificação própria, preservando o resto. {@code null} volta a herdar do pai. */
    public ProductVariant withPricing(Pricing newPricing) {
        return new ProductVariant(id, sku, attributes, active, newPricing);
    }
}
