package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationPreferenceJpaRepository
        extends JpaRepository<NotificationPreferenceEntity, NotificationPreferenceEntity.PreferenceId> {

    List<NotificationPreferenceEntity> findByUsername(String username);

    @Modifying
    @Query(value = """
            INSERT INTO notification_preferences (username, type, in_app_enabled, email_enabled)
            VALUES (:username, :type, :inApp, :email)
            ON CONFLICT (username, type) DO UPDATE SET
                in_app_enabled = EXCLUDED.in_app_enabled,
                email_enabled  = EXCLUDED.email_enabled
            """, nativeQuery = true)
    void upsert(@Param("username") String username, @Param("type") String type,
                @Param("inApp") boolean inApp, @Param("email") boolean email);
}
