package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.notification.NotificationPreference;

public record NotificationPreferenceResponseDTO(String type, boolean inAppEnabled, boolean emailEnabled) {

    public static NotificationPreferenceResponseDTO from(NotificationPreference p) {
        return new NotificationPreferenceResponseDTO(p.type().name(), p.inAppEnabled(), p.emailEnabled());
    }
}
