package com.securityspring.core.ports.out.notification;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.notification.Notification;

import java.time.Instant;

public interface NotificationRepository {

    Notification save(Notification notification);

    PageResult<Notification> findByUsername(String username, boolean unreadOnly, int page, int size);

    void markAsRead(Long id, String username);

    void markAllAsRead(String username);

    long countUnread(String username);

    void delete(Long id, String username);

    void deleteReadBefore(Instant before);
}
