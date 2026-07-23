package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "cash_register_sale")
public class SaleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<SaleItemEntity> items = new ArrayList<>();
}
