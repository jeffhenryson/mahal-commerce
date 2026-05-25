package com.securityspring.adapter.out.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;
}
