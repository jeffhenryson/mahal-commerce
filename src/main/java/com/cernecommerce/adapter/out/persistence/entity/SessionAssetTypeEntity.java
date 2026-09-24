package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

/** Tipo de utensílio da sessão (PDV-F021, V128). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "session_asset_type",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_asset_type_codigo", columnNames = "codigo"))
public class SessionAssetTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(name = "quantidade_total", nullable = false)
    private int quantidadeTotal;

    @Column(nullable = false)
    private boolean incluso;

    @Column(nullable = false)
    private boolean ativo;
}
