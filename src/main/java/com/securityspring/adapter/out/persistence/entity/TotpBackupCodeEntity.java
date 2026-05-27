package com.securityspring.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "totp_backup_codes",
        indexes = @Index(name = "idx_totp_backup_username", columnList = "username"),
        uniqueConstraints = @UniqueConstraint(name = "uk_totp_backup_code", columnNames = "code_hash"))
public class TotpBackupCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;
}
