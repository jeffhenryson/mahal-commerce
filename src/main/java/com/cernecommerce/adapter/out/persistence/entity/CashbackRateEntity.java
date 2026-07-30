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
@Table(name = "cashback_rate")
public class CashbackRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 10)
    private String scope;

    @Column(name = "scope_ref", length = 100)
    private String scopeRef;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal percent;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
