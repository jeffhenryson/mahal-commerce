package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item de uma {@link ComandaEntity} (PDV-F009). Tabela {@code comanda_item}.
 *
 * <p>{@code costPrice} é anulável — produto sem custo conhecido no lançamento fica nulo, nunca
 * zero, mesma convenção de {@code order_item.cost_price}.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "comanda_item")
public class ComandaItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comanda_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comanda_item_comanda"))
    @ToString.Exclude
    private ComandaEntity comanda;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    // PDV-F010 — por que a linha existe. Sem @Enumerated: mesma convenção enum-como-string de
    // product.type/product.unit.
    @Column(nullable = false, length = 20)
    private String mode;

    // Linha a preço zero que AINDA baixa estoque (promo "pague 1 leve 2", troca de open rosh).
    // Campo próprio, e não inferido de unit_price = 0: um desconto de 100% dá o mesmo zero.
    @Column(nullable = false)
    private boolean courtesy;

    // Liga o segundo sabor (ou a troca) à linha de origem, na mesma comanda.
    @Column(name = "linked_item_id")
    private Long linkedItemId;
}
