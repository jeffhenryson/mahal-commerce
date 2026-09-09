package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Lata aberta (EST-F027). A unicidade de "uma lata em uso por par SKU/depósito" é um índice único
 * <b>parcial</b> no banco ({@code WHERE closed_at IS NULL}, V124) e não uma
 * {@code @UniqueConstraint} aqui: a tabela é histórico, guarda todas as latas já fechadas do mesmo
 * par, e uma constraint simples proibiria a segunda. Mesmo molde dos índices parciais da V75.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "open_package")
public class OpenPackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private int uses;

    @Column(name = "sessions_per_unit", nullable = false)
    private int sessionsPerUnit;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_by", nullable = false, length = 100)
    private String openedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    // Enum como String, convenção do projeto (mesma de comanda_item.mode e stock_movement.type).
    @Column(name = "close_reason", length = 20)
    private String closeReason;
}
