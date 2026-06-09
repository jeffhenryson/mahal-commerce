package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationPreferenceJpaRepository
        extends JpaRepository<NotificationPreferenceEntity, NotificationPreferenceEntity.PreferenceId> {

    List<NotificationPreferenceEntity> findByUsername(String username);
}
