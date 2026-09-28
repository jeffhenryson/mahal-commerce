package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Adicional cobrado numa linha de sessão (PDV-F024, V133). Snapshot de nome e preço no lançamento —
 * {@code addon_id} fica para o relatório agrupar, sem FK dura, para o cadastro poder mudar.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "comanda_item_addon")
public class ComandaItemAddonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comanda_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_comanda_item_addon_item"))
    @ToString.Exclude
    private ComandaItemEntity item;

    @Column(name = "addon_id")
    private Long addonId;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;
}
