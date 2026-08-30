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

    // PDV-F015 — taxa de serviço, os 10% do garçom. Coluna PRÓPRIA, fora de net_amount, porque o
    // líquido é somado como receita em quatro agregações e a gorjeta é repassada, não faturada.
    // Só existe em channel = MESA (CHECK ck_sales_order_service_fee_only_mesa).
    @Column(name = "service_fee_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal serviceFeeAmount = BigDecimal.ZERO;

    // PDV-F010 — origem de mesa. Só preenchidos em channel = MESA (CHECK ck_sales_order_mesa_origin).
    // comanda_id é redundante com comanda.order_id, que aponta de volta: a redundância evita join
    // reverso em toda página de Vendas > Pedidos.
    @Column(name = "comanda_id")
    private Long comandaId;

    // Rótulo congelado no fechamento, não lido da comanda — renomear a mesa depois não pode
    // reescrever o histórico. Mesma razão de order_item.product_name.
    @Column(name = "table_label", length = 100)
    private String tableLabel;

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

    // PDV-F008 — venda de balcão reservada para retirada depois. Histórico, sem CHECK de
    // coexistência com "status" (diferente de cancelled_at/refunded_at): permanece preenchido
    // mesmo depois de RESERVADO -> CONCLUIDO (retirada), mesma régua de paid_at.
    @Column(name = "reserved_at")
    private Instant reservedAt;

    // Timestamps por etapa da esteira de fulfillment (BACKEND_TODO.md do mahal-admin,
    // §"Vendas: timestamps por etapa"). Histórico, sem CHECK de coexistência com "status" — mesma
    // régua de reserved_at/paid_at.
    @Column(name = "separated_at")
    private Instant separatedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /**
     * Bloqueio otimista. A {@code Sale} anterior não tinha — era irrelevante numa tabela
     * insert-only, e passa a importar quando o pedido ganha transição de estado.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    // @OrderBy porque o pedido é lido na ordem de lançamento — é assim que o comprovante imprime.
    // Sem isto a ordem é o que o banco quiser devolver: invisível enquanto cada pedido vinha de uma
    // consulta própria, e dependente da intercalação do join desde que PED-C002 passou a trazer
    // vários de uma vez. Mesmo padrão de ComandaEntity.items.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @ToString.Exclude
    private List<OrderItemEntity> items = new ArrayList<>();
}
