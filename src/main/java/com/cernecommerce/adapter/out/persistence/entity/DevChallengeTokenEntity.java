package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dev_challenge_tokens",
        indexes = {
            @Index(name = "idx_dev_challenge_hash",    columnList = "token_hash"),
            @Index(name = "idx_dev_challenge_expires", columnList = "expires_at")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_dev_challenge_token", columnNames = "token_hash"))
public class DevChallengeTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "period_t", nullable = false)
    private long periodT;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
