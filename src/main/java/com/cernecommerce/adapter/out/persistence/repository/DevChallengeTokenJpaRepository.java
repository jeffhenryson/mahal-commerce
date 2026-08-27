package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.DevChallengeTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DevChallengeTokenJpaRepository extends JpaRepository<DevChallengeTokenEntity, Long> {

    Optional<DevChallengeTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update DevChallengeTokenEntity d set d.usedAt = :now where d.tokenHash = :tokenHash and d.usedAt is null")
    int consumeIfAvailable(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from DevChallengeTokenEntity d where d.expiresAt < :before")
    void deleteExpiredBefore(@Param("before") Instant before);
}
