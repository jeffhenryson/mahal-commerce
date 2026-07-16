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
@Table(name = "stock_balance", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_balance_sku_warehouse", columnNames = {"sku", "warehouse_id"}))
public class StockBalanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Version
    @Column(nullable = false)
    private long version;
}
