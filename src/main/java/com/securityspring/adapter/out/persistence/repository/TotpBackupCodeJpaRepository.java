package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.TotpBackupCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TotpBackupCodeJpaRepository extends JpaRepository<TotpBackupCodeEntity, Long> {
    Optional<TotpBackupCodeEntity> findByCodeHash(String codeHash);

    @Modifying
    @Query("update TotpBackupCodeEntity b set b.usedAt = :now where b.codeHash = :codeHash and b.usedAt is null")
    int markAsUsedIfAvailable(@Param("codeHash") String codeHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from TotpBackupCodeEntity b where b.username = :username")
    void deleteByUsername(@Param("username") String username);

    @Query("select count(b) from TotpBackupCodeEntity b where b.username = :username and b.usedAt is null")
    int countRemainingByUsername(@Param("username") String username);
}
