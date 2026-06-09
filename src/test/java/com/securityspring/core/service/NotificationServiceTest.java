package com.securityspring.core.service;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.notification.Notification;
import com.securityspring.core.domain.model.notification.NotificationType;
import com.securityspring.core.ports.out.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repository;

    @InjectMocks
    NotificationService service;

    @Test
    void notify_salva_notificacao_com_campos_corretos() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification result = service.notify("alice", NotificationType.PASSWORD_CHANGED, "Senha alterada", "Detalhes aqui.");

        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.id()).isNull();
        assertThat(saved.username()).isEqualTo("alice");
        assertThat(saved.type()).isEqualTo(NotificationType.PASSWORD_CHANGED);
        assertThat(saved.title()).isEqualTo("Senha alterada");
        assertThat(saved.body()).isEqualTo("Detalhes aqui.");
        assertThat(saved.readAt()).isNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(result).isNotNull();
    }

    @Test
    void getNotifications_delega_para_repositorio_com_parametros_corretos() {
        PageResult<Notification> expected = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(repository.findByUsername("alice", false, 0, 20)).thenReturn(expected);

        PageResult<Notification> result = service.getNotifications("alice", false, 0, 20);

        assertThat(result).isEqualTo(expected);
        verify(repository).findByUsername("alice", false, 0, 20);
    }

    @Test
    void getNotifications_unreadOnly_passa_flag_corretamente() {
        PageResult<Notification> expected = new PageResult<>(List.of(), 0, 10, 0L, 0);
        when(repository.findByUsername("alice", true, 0, 10)).thenReturn(expected);

        PageResult<Notification> result = service.getNotifications("alice", true, 0, 10);

        assertThat(result).isEqualTo(expected);
        verify(repository).findByUsername("alice", true, 0, 10);
    }

    @Test
    void markAsRead_marca_quando_notificacao_pertence_ao_usuario() {
        Notification n = new Notification(42L, "alice", NotificationType.SYSTEM, "t", "b", null, Instant.now());
        when(repository.findById(42L)).thenReturn(Optional.of(n));

        service.markAsRead("alice", 42L);

        verify(repository).markAsRead(42L);
    }

    @Test
    void markAsRead_nao_marca_quando_notificacao_pertence_a_outro_usuario() {
        Notification n = new Notification(42L, "bob", NotificationType.SYSTEM, "t", "b", null, Instant.now());
        when(repository.findById(42L)).thenReturn(Optional.of(n));

        service.markAsRead("alice", 42L);

        verify(repository, never()).markAsRead(any());
    }

    @Test
    void markAsRead_nao_marca_quando_notificacao_nao_existe() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        service.markAsRead("alice", 99L);

        verify(repository, never()).markAsRead(any());
    }

    @Test
    void markAllAsRead_delega_para_repositorio() {
        service.markAllAsRead("alice");

        verify(repository).markAllAsRead("alice");
    }

    @Test
    void countUnread_retorna_valor_do_repositorio() {
        when(repository.countUnread("alice")).thenReturn(7L);

        long count = service.countUnread("alice");

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void delete_remove_quando_notificacao_pertence_ao_usuario() {
        Notification n = new Notification(42L, "alice", NotificationType.SYSTEM, "t", "b", null, Instant.now());
        when(repository.findById(42L)).thenReturn(Optional.of(n));

        service.delete("alice", 42L);

        verify(repository).delete(42L);
    }

    @Test
    void delete_nao_remove_quando_notificacao_pertence_a_outro_usuario() {
        Notification n = new Notification(42L, "bob", NotificationType.SYSTEM, "t", "b", null, Instant.now());
        when(repository.findById(42L)).thenReturn(Optional.of(n));

        service.delete("alice", 42L);

        verify(repository, never()).delete(any());
    }

    @Test
    void delete_nao_remove_quando_notificacao_nao_existe() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        service.delete("alice", 99L);

        verify(repository, never()).delete(any());
    }
}
