package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "stock_movement")
public class StockMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
