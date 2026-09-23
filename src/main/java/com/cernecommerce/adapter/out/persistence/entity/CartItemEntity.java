package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// Sem @UniqueConstraint desde ECM-F008: (cart_id, sku) só é único para linha AVULSA — índice
// parcial na V126, que o JPA não sabe declarar. A linha avulsa continua única por construção em
// CartRepositoryImpl.upsertItem.
@Table(name = "cart_item")
public class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_cart"))
    @ToString.Exclude
    private CartEntity cart;

    // Texto livre, sem FK para product — mesma convenção de stock_balance/stock_movement/
    // stock_reservation.sku: o produto pode ser renomeado/removido sem corromper o carrinho.
    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    // ECM-F008 — pacote de kit montável. Os três nulos = linha avulsa.
    @Column(name = "kit_bundle_id", length = 36)
    private String kitBundleId;

    @Column(name = "kit_template_id")
    private Long kitTemplateId;

    @Column(name = "kit_step_id")
    private Long kitStepId;
}
