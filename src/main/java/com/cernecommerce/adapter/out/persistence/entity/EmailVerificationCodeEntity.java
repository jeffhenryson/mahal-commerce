package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// sentAt é preenchido via @PrePersist — não usar @Data aqui (evitar override de equals/hashCode com id nulo)

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_verification_codes",
        indexes = @Index(name = "idx_evc_code", columnList = "code"),
        uniqueConstraints = @UniqueConstraint(name = "uk_evc_code", columnNames = "code"))
public class EmailVerificationCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false, updatable = false)
    private Instant sentAt;

    @PrePersist
    void prePersist() {
        if (sentAt == null) sentAt = Instant.now();
    }
}
