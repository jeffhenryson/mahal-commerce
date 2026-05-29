package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    @Query("""
        SELECT a FROM AuditLogEntity a
        WHERE (:username IS NULL OR a.username = :username)
          AND (:action   IS NULL OR a.action   = :action)
        ORDER BY a.timestamp DESC
        """)
    Page<AuditLogEntity> findFiltered(
        @Param("username") String username,
        @Param("action")   String action,
        Pageable pageable);
}
