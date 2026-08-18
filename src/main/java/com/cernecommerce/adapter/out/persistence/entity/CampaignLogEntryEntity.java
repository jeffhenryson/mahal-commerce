package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "campaign_log")
public class CampaignLogEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "automation_id", nullable = false)
    private Long automationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignDispatchStatus status;

    @Column(name = "disparado_em", nullable = false)
    private Instant disparadoEm;

    @Column(name = "convertido_em")
    private Instant convertidoEm;

    @Column(name = "erro_detalhe", length = 500)
    private String erroDetalhe;
}
