package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Comanda de mesa (PDV-F009). Tabela {@code comanda}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "comanda")
public class ComandaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** Depósito da sessão que abriu a comanda (PDV-C004). */
    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "table_or_customer_label", nullable = false, length = 100)
    private String tableOrCustomerLabel;

    @Column(nullable = false, length = 20)
    private String status;

    /** Preenchido só no fechamento. Nulo em ABERTA e em CANCELADA. */
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "opened_by", nullable = false, length = 80)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ComandaItemEntity> items = new ArrayList<>();
}
