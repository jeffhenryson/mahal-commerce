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
@Table(name = "customer_stage_transitions")
public class StageTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStage de;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStage para;

    @Column(nullable = false, length = 80)
    private String autor;

    @Column(name = "transicionado_em", nullable = false)
    private Instant transicionadoEm;
}
