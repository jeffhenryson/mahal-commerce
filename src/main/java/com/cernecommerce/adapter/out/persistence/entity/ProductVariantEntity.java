package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product_variant", uniqueConstraints = @UniqueConstraint(name = "uk_product_variant_sku", columnNames = "sku"))
public class ProductVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_variant_product"))
    @ToString.Exclude
    private ProductEntity product;

    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    @Column(nullable = false)
    private boolean active;

    @ElementCollection
    @CollectionTable(name = "product_attribute", joinColumns = @JoinColumn(name = "variant_id",
            foreignKey = @ForeignKey(name = "fk_product_attribute_variant")))
    private Set<ProductAttributeEmbeddable> attributes = new LinkedHashSet<>();
}
