package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.NotificationEntity;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.notification.Notification;
import com.securityspring.core.domain.model.notification.NotificationType;
import com.securityspring.core.ports.out.notification.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepo;

    public NotificationRepositoryImpl(NotificationJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification save(Notification notification) {
        NotificationEntity entity = toEntity(notification);
        return toDomain(jpaRepo.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Notification> findByUsername(String username, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationEntity> result = unreadOnly
                ? jpaRepo.findUnreadByUsername(username, pageable)
                : jpaRepo.findByUsername(username, pageable);
        List<Notification> content = result.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(Long id) {
        return jpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public void markAsRead(Long id) {
        jpaRepo.markAsRead(id, Instant.now());
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        jpaRepo.markAllAsRead(username, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(String username) {
        return jpaRepo.countByUsernameAndReadAtIsNull(username);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        jpaRepo.deleteById(id);
    }

    private NotificationEntity toEntity(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.setId(n.id());
        e.setUsername(n.username());
        e.setType(n.type().name());
        e.setTitle(n.title());
        e.setBody(n.body());
        e.setReadAt(n.readAt());
        e.setCreatedAt(n.createdAt());
        return e;
    }

    private Notification toDomain(NotificationEntity e) {
        return new Notification(
                e.getId(),
                e.getUsername(),
                NotificationType.valueOf(e.getType()),
                e.getTitle(),
                e.getBody(),
                e.getReadAt(),
                e.getCreatedAt()
        );
    }
}
