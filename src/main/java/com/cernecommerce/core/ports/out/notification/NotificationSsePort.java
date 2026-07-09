package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.Notification;

public interface NotificationSsePort {
    void send(String username, Notification notification);
    int activeConnections(String username);
}
