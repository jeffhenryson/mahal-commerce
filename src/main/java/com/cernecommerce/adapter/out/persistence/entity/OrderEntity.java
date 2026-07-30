package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de venda de qualquer canal (PDV-F003). Tabela {@code sales_order}, renomeada de
 * {@code cash_register_sale} pela V65.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "sales_order")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "order_number", length = 30)
    private String orderNumber;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    /** Bruto do pedido. Mantém o nome de coluna de V57 para não quebrar o dado legado. */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "cashback_redeemed", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashbackRedeemed;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "change_amount", precision = 14, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** Instante da criação. Mantém o nome de coluna {@code sold_at} de V57. */
    @Column(name = "sold_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "concluded_at")
    private Instant concludedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    /**
     * Bloqueio otimista. A {@code Sale} anterior não tinha — era irrelevante numa tabela
     * insert-only, e passa a importar quando o pedido ganha transição de estado.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<OrderItemEntity> items = new ArrayList<>();
}
