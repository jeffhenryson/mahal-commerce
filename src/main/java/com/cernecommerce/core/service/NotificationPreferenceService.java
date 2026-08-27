package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.core.ports.out.notification.NotificationPreferenceRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class NotificationPreferenceService implements NotificationPreferenceUseCase {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreference> getPreferences(String username) {
        Map<NotificationType, NotificationPreference> stored = preferenceRepository.findByUsername(username);
        return Arrays.stream(NotificationType.values())
                .map(type -> stored.getOrDefault(type, NotificationPreference.defaultFor(username, type)))
                .toList();
    }

    @Override
    @Transactional
    public void updatePreference(String username, NotificationType type, boolean inAppEnabled, boolean emailEnabled) {
        preferenceRepository.upsert(new NotificationPreference(username, type, inAppEnabled, emailEnabled));
    }
}
