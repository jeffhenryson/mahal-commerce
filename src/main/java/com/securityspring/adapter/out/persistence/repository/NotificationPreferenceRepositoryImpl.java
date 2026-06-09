package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.NotificationPreferenceEntity;
import com.securityspring.core.domain.model.notification.NotificationPreference;
import com.securityspring.core.domain.model.notification.NotificationType;
import com.securityspring.core.ports.out.notification.NotificationPreferenceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class NotificationPreferenceRepositoryImpl implements NotificationPreferenceRepository {

    private final NotificationPreferenceJpaRepository jpaRepo;

    public NotificationPreferenceRepositoryImpl(NotificationPreferenceJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<NotificationType, NotificationPreference> findByUsername(String username) {
        return jpaRepo.findByUsername(username).stream()
                .collect(Collectors.toMap(
                        e -> NotificationType.valueOf(e.getType()),
                        this::toDomain));
    }

    @Override
    @Transactional
    public void upsert(NotificationPreference preference) {
        NotificationPreferenceEntity.PreferenceId id =
                new NotificationPreferenceEntity.PreferenceId(preference.username(), preference.type().name());
        NotificationPreferenceEntity entity = jpaRepo.findById(id)
                .orElseGet(NotificationPreferenceEntity::new);
        entity.setUsername(preference.username());
        entity.setType(preference.type().name());
        entity.setInAppEnabled(preference.inAppEnabled());
        entity.setEmailEnabled(preference.emailEnabled());
        jpaRepo.save(entity);
    }

    private NotificationPreference toDomain(NotificationPreferenceEntity e) {
        return new NotificationPreference(
                e.getUsername(),
                NotificationType.valueOf(e.getType()),
                e.isInAppEnabled(),
                e.isEmailEnabled());
    }
}
