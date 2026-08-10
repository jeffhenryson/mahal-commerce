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

    /**
     * Nome da categoria, como texto livre. Continua sendo o caminho aceito e é o que o admin usa
     * hoje: um nome desconhecido <b>cria</b> a categoria correspondente. Quem já souber o id
     * prefira {@code categoryId}, que dispensa a busca por nome.
     */
    @Size(max = 100)
    private String category;

    /**
     * Vínculo direto com uma categoria existente. Quando informado, vence sobre {@code category}
     * e o nome é resolvido a partir dele. Id inexistente é 404.
     */
    private Long categoryId;

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

    /**
     * Atributos descritivos do próprio produto, opcional. Distintos dos que vão dentro de
     * {@code variants}: aqueles fazem parte do que identifica cada variação da grade, estes só
     * descrevem o item — existem para o produto <b>sem</b> grade, que não tinha onde carregar
     * um "Sabor: Menta".
     */
    @Valid
    @Size(max = 20)
    private List<ProductAttributeRequest> attributes;

    @Valid
    private List<ProductVariantRequest> variants;

    /** Opcional (EST-F019) — omitido, o produto nasce sem precificação. */
    @Valid
    private PricingRequest pricing;

    /**
     * Indica se este request mexe em preço em <b>qualquer</b> nível — na raiz ou dentro de alguma
     * variação (EST-F020).
     *
     * <p>Existe para o {@code @PreAuthorize} do controller. Antes de haver preço por variação, a
     * expressão checava {@code #request.pricing == null} e isso bastava. Com o preço podendo vir
     * dentro de {@code variants[]}, aquela checagem virou um furo: quem tem apenas
     * {@code ESTOQUE_PRODUCT_MANAGE} passaria a precificar pela porta lateral, desfazendo a
     * separação que EST-F019 criou de propósito entre manter cadastro e mexer em preço.</p>
     */
    public boolean touchesPricing() {
        if (pricing != null) {
            return true;
        }
        return variants != null && variants.stream().anyMatch(v -> v != null && v.getPricing() != null);
    }
}
