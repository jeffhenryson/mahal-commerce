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

    /**
     * Parte de {@code quantity} já prometida a um pedido que ainda não saiu (EST-F021). Fica nesta
     * linha, e não numa soma sobre {@code stock_reservation}, para cair sob o mesmo {@code @Version}
     * do saldo — é isso que faz duas reservas concorrentes colidirem em vez de ambas passarem.
     */
    @Column(name = "reserved_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal reservedQuantity;

    @Version
    @Column(nullable = false)
    private long version;
}
