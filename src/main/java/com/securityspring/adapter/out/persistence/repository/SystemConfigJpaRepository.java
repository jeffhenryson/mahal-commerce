package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigJpaRepository extends JpaRepository<SystemConfigEntity, String> {
}
