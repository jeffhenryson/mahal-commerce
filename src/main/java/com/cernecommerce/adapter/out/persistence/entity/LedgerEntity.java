package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lançamento de fluxo de caixa (FIN-F004). Tabela {@code cash_flow_entry} (V103).
 *
 * <p>Enums gravados como {@code String}, sem {@code @Enumerated} — mesma convenção do resto do
 * projeto (ver {@code OrderEntity.status}), conversão manual em {@code LedgerRepositoryImpl}.
 * {@code linkedEntityId} é id solto, sem {@code @ManyToOne}/FK: pode apontar para
 * {@code sales_order} ou {@code goods_receipt} dependendo de {@code linkedEntityType} — mesma
 * razão de {@code ProductEntity.categoryId} não navegar via JPA.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "cash_flow_entry")
public class LedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "entity_name", length = 150)
    private String entityName;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "linked_entity_type", length = 20)
    private String linkedEntityType;

    @Column(name = "linked_entity_id")
    private Long linkedEntityId;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
