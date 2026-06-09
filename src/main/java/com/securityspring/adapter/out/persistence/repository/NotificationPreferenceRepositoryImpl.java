package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.NotificationPreferenceEntity;
import com.securityspring.core.domain.model.notification.NotificationPreference;
import com.securityspring.core.domain.model.notification.NotificationType;
import com.securityspring.core.ports.out.notification.NotificationPreferenceRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class NotificationPreferenceRepositoryImpl implements NotificationPreferenceRepository {

    static final String CACHE_NAME = "notificationPreferences";

    private final NotificationPreferenceJpaRepository jpaRepo;

    public NotificationPreferenceRepositoryImpl(NotificationPreferenceJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_NAME, key = "#username")
    public Map<NotificationType, NotificationPreference> findByUsername(String username) {
        return jpaRepo.findByUsername(username).stream()
                .collect(Collectors.toMap(
                        e -> NotificationType.valueOf(e.getType()),
                        this::toDomain));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#preference.username()")
    public void upsert(NotificationPreference preference) {
        jpaRepo.upsert(preference.username(), preference.type().name(),
                preference.inAppEnabled(), preference.emailEnabled());
    }

    private NotificationPreference toDomain(NotificationPreferenceEntity e) {
        return new NotificationPreference(
                e.getUsername(),
                NotificationType.valueOf(e.getType()),
                e.isInAppEnabled(),
                e.isEmailEnabled());
    }
}
