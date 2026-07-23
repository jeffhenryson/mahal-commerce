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
@Table(name = "cash_register_session")
public class CashRegisterSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 80)
    private String operator;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opening_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(nullable = false, length = 10)
    private String status;
}
