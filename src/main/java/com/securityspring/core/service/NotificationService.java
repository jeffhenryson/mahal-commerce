package com.securityspring.core.service;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.notification.Notification;
import com.securityspring.core.domain.model.notification.NotificationType;
import com.securityspring.core.ports.in.NotificationUseCase;
import com.securityspring.core.ports.out.notification.NotificationRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public class NotificationService implements NotificationUseCase {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void notify(String username, NotificationType type, String title, String body) {
        notificationRepository.save(new Notification(null, username, type, title, body, null, Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Notification> getNotifications(String username, boolean unreadOnly, int page, int size) {
        return notificationRepository.findByUsername(username, unreadOnly, page, size);
    }

    @Override
    @Transactional
    public void markAsRead(String username, Long notificationId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.username().equals(username))
                .ifPresent(n -> notificationRepository.markAsRead(notificationId));
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        notificationRepository.markAllAsRead(username);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(String username) {
        return notificationRepository.countUnread(username);
    }

    @Override
    @Transactional
    public void delete(String username, Long notificationId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.username().equals(username))
                .ifPresent(n -> notificationRepository.delete(notificationId));
    }
}
