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
// Sem @UniqueConstraint aqui de propósito (mesmo caso de CashbackRateEntity/uk_cashback_rate_
// active_scope): a unicidade real é condicional — (stock_count_id, sku) só quando lot_code é
// nulo, (stock_count_id, sku, lot_code) quando não é — e @UniqueConstraint não expressa WHERE.
// Os dois índices únicos parciais vivem só na migration (V75); aqui a garantia é de aplicação,
// em EstoqueService.recordCountedItem/StockCount.withCountedItem.
@Table(name = "stock_count_item")
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

    @Column(name = "lot_code", length = 50)
    private String lotCode;
}
