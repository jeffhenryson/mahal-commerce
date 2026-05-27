package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.TotpConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TotpConfigJpaRepository extends JpaRepository<TotpConfigEntity, Long> {
    Optional<TotpConfigEntity> findByUsername(String username);

    @Modifying
    @Query("update TotpConfigEntity t set t.enabled = true, t.confirmedAt = :confirmedAt where t.username = :username")
    void enable(@Param("username") String username, @Param("confirmedAt") Instant confirmedAt);

    @Modifying
    @Query("delete from TotpConfigEntity t where t.username = :username")
    void deleteByUsername(@Param("username") String username);
}
