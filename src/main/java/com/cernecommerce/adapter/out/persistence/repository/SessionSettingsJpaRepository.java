package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SessionSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSettingsJpaRepository extends JpaRepository<SessionSettingsEntity, Short> {
}
