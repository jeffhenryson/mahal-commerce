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

    // EST-F015 (Fatia 6) — SIMPLES ou KIT. Sem @Enumerated: mesma convenção enum-como-string do
    // resto do projeto (ver OrderEntity.status), conversão manual em ProductRepositoryImpl.
    @Column(nullable = false, length = 20)
    private String type;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ProductVariantEntity> variants = new ArrayList<>();
}
