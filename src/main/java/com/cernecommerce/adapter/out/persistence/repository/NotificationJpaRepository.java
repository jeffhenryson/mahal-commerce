package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long> {

    Page<NotificationEntity> findByUsername(String username, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.username = :username AND n.readAt IS NULL")
    Page<NotificationEntity> findUnreadByUsername(@Param("username") String username, Pageable pageable);

    long countByUsernameAndReadAtIsNull(String username);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.readAt = :now WHERE n.id = :id AND n.username = :username AND n.readAt IS NULL")
    void markAsRead(@Param("id") Long id, @Param("username") String username, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.id = :id AND n.username = :username")
    void deleteByIdAndUsername(@Param("id") Long id, @Param("username") String username);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.readAt = :now WHERE n.username = :username AND n.readAt IS NULL")
    void markAllAsRead(@Param("username") String username, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.readAt IS NOT NULL AND n.readAt < :before")
    int deleteReadBefore(@Param("before") Instant before);
}
