package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product", uniqueConstraints = @UniqueConstraint(name = "uk_product_sku", columnNames = "sku"))
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String brand;

    // Estágio 01 do admin — link de imagem cadastrado manualmente pelo lojista, não upload.
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    // Estágio 01 do admin — produto em promoção; o marketplace filtra por este campo.
    @Column(name = "on_sale", nullable = false)
    private boolean onSale;

    // Selo de destaque distinto de onSale — segundo nível de promoção, sem filtro de query
    // próprio (diferente de onSale, que é parâmetro de GET /shop/catalog).
    @Column(name = "super_promo", nullable = false)
    private boolean superPromo;

    // Descrição longa do produto, sem limite curto como name. Sem @Lob: TEXT é a convenção já
    // usada no projeto (ver AuditLogEntity, CustomerNoteEntity, NotificationEntity).
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Estágio 01 do admin — link de vídeo cadastrado manualmente, mesma convenção de imageUrl.
    @Column(name = "video_url", length = 2048)
    private String videoUrl;

    @Column(nullable = false)
    private boolean active;

    // EST-F019 — precificação. Nullable: produto não precificado é estado válido, e preço zero
    // não é o mesmo que preço desconhecido. Ver V63 e o value object Pricing.
    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "markup_percent", precision = 9, scale = 4)
    private BigDecimal markupPercent;

    @Column(name = "sale_price", precision = 14, scale = 2)
    private BigDecimal salePrice;

    // Preço "de/por" — puramente valor de exibição, sem checagem relacional contra salePrice
    // (ver Pricing.hasDiscount, que decide na leitura se o desconto exibido faz sentido).
    @Column(name = "original_price", precision = 14, scale = 2)
    private BigDecimal originalPrice;

    // EST-F015 (Fatia 6) — SIMPLES ou KIT. Sem @Enumerated: mesma convenção enum-como-string do
    // resto do projeto (ver OrderEntity.status), conversão manual em ProductRepositoryImpl.
    @Column(nullable = false, length = 20)
    private String type;

    // EST-F008 — opt-in de rastreamento de lote/validade. Kit nunca é lote-rastreado (invariante
    // no domínio, não no schema — mesma régua de outras regras entre campos deste projeto).
    @Column(name = "lot_tracked", nullable = false)
    private boolean lotTracked;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ProductVariantEntity> variants = new ArrayList<>();

    // Galeria de até 5 imagens ordenadas. @ElementCollection (mesmo padrão leve de
    // ProductAttribute/product_attribute) em vez de entidade completa: não há requisito de CRUD
    // independente por imagem, só substituição da lista inteira via PATCH.
    @ElementCollection
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id",
            foreignKey = @ForeignKey(name = "fk_product_image_product")))
    @OrderColumn(name = "image_order")
    @Column(name = "url", length = 2048)
    private List<String> images = new ArrayList<>();
}
