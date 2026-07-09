package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;

public interface NotificationUseCase {

    Notification notify(String username, NotificationType type, String title, String body);

    PageResult<Notification> getNotifications(String username, boolean unreadOnly, int page, int size);

    void markAsRead(String username, Long notificationId);

    void markAllAsRead(String username);

    long countUnread(String username);

    void delete(String username, Long notificationId);
}
