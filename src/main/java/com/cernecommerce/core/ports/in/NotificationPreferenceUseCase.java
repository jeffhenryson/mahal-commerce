package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;

import java.util.List;

public interface NotificationPreferenceUseCase {

    List<NotificationPreference> getPreferences(String username);

    void updatePreference(String username, NotificationType type, boolean inAppEnabled, boolean emailEnabled);
}
