package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.notification.Notification;
import com.securityspring.core.domain.model.notification.NotificationType;

public interface NotificationUseCase {

    void notify(String username, NotificationType type, String title, String body);

    PageResult<Notification> getNotifications(String username, boolean unreadOnly, int page, int size);

    void markAsRead(String username, Long notificationId);

    void markAllAsRead(String username);

    long countUnread(String username);
}
