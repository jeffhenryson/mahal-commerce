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
@Table(name = "stock_count_item", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_count_item_count_sku", columnNames = {"stock_count_id", "sku"}))
public class StockCountItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_id", nullable = false)
    @ToString.Exclude
    private StockCountEntity stockCount;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "counted_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal countedQuantity;

    /** Nulo enquanto a contagem está aberta; preenchido no fechamento. */
    @Column(name = "expected_quantity", precision = 14, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(precision = 14, scale = 3)
    private BigDecimal difference;
}
