package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
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
@Table(name = "campaign_automations")
public class CampaignAutomationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignTrigger gatilho;

    @Enumerated(EnumType.STRING)
    @Column(name = "segmento_alvo", nullable = false, length = 20)
    private CustomerStage segmentoAlvo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CampaignChannel canal;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String template;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /** JSON serializado de {@code Map<String,String>} — mesmo padrão de {@code AuditLogRepositoryImpl}. */
    @Column(name = "webhook_headers", columnDefinition = "TEXT")
    private String webhookHeaders;
}
