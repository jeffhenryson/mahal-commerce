package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.crm.CustomerStage;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(name = "uk_customers_email", columnNames = "email"))
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(nullable = false, length = 30)
    private String contato;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(length = 11)
    private String cpf;

    @Column(length = 100)
    private String origem;

    @Column(name = "cadastrado_em", nullable = false)
    private Instant cadastradoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStage estagio;
}
