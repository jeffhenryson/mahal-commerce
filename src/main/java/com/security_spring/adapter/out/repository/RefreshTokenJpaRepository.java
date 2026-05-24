package com.security_spring.adapter.out.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.security_spring.adapter.out.entities.RefreshTokenEntity;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    long deleteByUser_Id(Long userId);
    long deleteByExpiresAtBefore(Instant instant);
}
