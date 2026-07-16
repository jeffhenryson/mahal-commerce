package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "warehouse", uniqueConstraints = @UniqueConstraint(name = "uk_warehouse_code", columnNames = "code"))
public class WarehouseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarehouseType type;

    @Column(nullable = false)
    private boolean active;
}
