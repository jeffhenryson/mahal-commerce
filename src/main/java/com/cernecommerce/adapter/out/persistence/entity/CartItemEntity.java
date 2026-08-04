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
@Table(name = "cart_item", uniqueConstraints = @UniqueConstraint(name = "uk_cart_item_cart_sku", columnNames = {"cart_id", "sku"}))
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
}
