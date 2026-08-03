package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "stock_lot", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_lot_sku_warehouse_lot", columnNames = {"sku", "warehouse_id", "lot_code"}))
public class StockLotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "lot_code", nullable = false, length = 50)
    private String lotCode;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "alerted_at")
    private Instant alertedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
