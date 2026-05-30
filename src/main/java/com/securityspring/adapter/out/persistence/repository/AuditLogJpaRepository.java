package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long>,
        JpaSpecificationExecutor<AuditLogEntity> {

    @Modifying
    @Query("DELETE FROM AuditLogEntity a WHERE a.timestamp < :cutoff")
    long deleteOlderThan(@Param("cutoff") Instant cutoff);
}
