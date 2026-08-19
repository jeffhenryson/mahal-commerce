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
@Table(name = "replenishment_list_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_replenishment_sku_warehouse", columnNames = {"sku", "warehouse_id"}))
public class ReplenishmentListItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "product_name_snapshot", length = 255)
    private String productNameSnapshot;

    @Column(name = "category_snapshot", length = 100)
    private String categorySnapshot;

    @Column(name = "brand_snapshot", length = 100)
    private String brandSnapshot;

    @Column(name = "unit_snapshot", length = 10)
    private String unitSnapshot;

    @Column(name = "current_stock_snapshot", precision = 14, scale = 3)
    private BigDecimal currentStockSnapshot;

    @Column(name = "min_stock_snapshot", precision = 14, scale = 3)
    private BigDecimal minStockSnapshot;

    @Column(name = "suggested_quantity_snapshot", precision = 14, scale = 3)
    private BigDecimal suggestedQuantitySnapshot;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost_snapshot", precision = 14, scale = 2)
    private BigDecimal unitCostSnapshot;

    @Column(name = "previous_purchase_qty_snapshot", precision = 14, scale = 3)
    private BigDecimal previousPurchaseQuantitySnapshot;

    @Column(name = "previous_purchase_cost_snapshot", precision = 14, scale = 2)
    private BigDecimal previousPurchaseUnitCostSnapshot;

    @Column(name = "previous_purchased_at_snapshot")
    private Instant previousPurchasedAtSnapshot;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;
}
