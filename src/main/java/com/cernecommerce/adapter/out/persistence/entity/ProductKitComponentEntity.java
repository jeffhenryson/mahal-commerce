package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Linha da receita de um kit virtual (EST-F015). Sem FK para {@code product}: {@code
 * component_sku} pode ser SKU de variação, não só de SKU pai — o mesmo espaço de nomes
 * compartilhado que já impede uma FK direta em {@code stock_balance}/{@code stock_movement}. A
 * checagem "componente existe e é SIMPLES" é feita no service via {@code findByAnySku}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product_kit_component",
        uniqueConstraints = @UniqueConstraint(name = "uk_kit_component", columnNames = {"kit_sku", "component_sku"}))
public class ProductKitComponentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "kit_sku", nullable = false, length = 50)
    private String kitSku;

    @Column(name = "component_sku", nullable = false, length = 50)
    private String componentSku;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
}
