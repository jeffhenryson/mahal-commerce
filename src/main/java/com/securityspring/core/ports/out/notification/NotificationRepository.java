package com.securityspring.core.ports.out.notification;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.notification.Notification;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    PageResult<Notification> findByUsername(String username, boolean unreadOnly, int page, int size);

    Optional<Notification> findById(Long id);

    void markAsRead(Long id);

    void markAllAsRead(String username);

    long countUnread(String username);

    void delete(Long id);

    void deleteReadBefore(Instant before);
}
