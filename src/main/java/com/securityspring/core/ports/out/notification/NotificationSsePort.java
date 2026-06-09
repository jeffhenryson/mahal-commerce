package com.securityspring.core.ports.out.notification;

import com.securityspring.core.domain.model.notification.Notification;

public interface NotificationSsePort {
    void send(String username, Notification notification);
    int activeConnections(String username);
}
