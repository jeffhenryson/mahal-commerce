package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Utensílio alocado a uma linha de sessão (PDV-F021, V128). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "comanda_session_asset")
public class ComandaSessionAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "comanda_item_id")
    private Long comandaItemId;

    @Column(name = "asset_type_id", nullable = false)
    private Long assetTypeId;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "alocado_em", nullable = false)
    private Instant alocadoEm;

    @Column(name = "liberado_em")
    private Instant liberadoEm;
}
