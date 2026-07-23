package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "supplier")
public class SupplierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "tax_id", nullable = false, length = 20, unique = true)
    private String taxId;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean active;
}
